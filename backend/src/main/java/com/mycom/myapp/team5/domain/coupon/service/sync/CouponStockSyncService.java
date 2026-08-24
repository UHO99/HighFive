package com.mycom.myapp.team5.domain.coupon.service.sync;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStreamPendingChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S012 - CLOSE된 쿠폰의 재고를 coupon_issue 실 발급 건수 기준으로 동기화한다.
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStockSyncService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponStreamPendingChecker pendingChecker;

    @Scheduled(fixedDelay = 5000)
    public void syncClosedCoupons() {
        List<Coupon> targets = couponRepository.findByStatusAndIssuedQuantityIsNull(CouponStatus.CLOSE);
        if (targets.isEmpty()) {
            return;
        }

        // Redis PEL 확인은 스트림별로 따로 물어봐야 하지만(멀티키 XPENDING이 없음), DB 카운트는
        // 드레인된 쿠폰들만 모아 한 번의 GROUP BY 쿼리로 묶어 N+1을 없앤다.
        List<Coupon> drained = targets.stream()
                .filter(coupon -> isDrained(coupon.getId()))
                .toList();
        if (drained.isEmpty()) {
            log.info("정합성 동기화 대기 - 드레인된 쿠폰 없음. 대상={}건", targets.size());
            return;
        }

        Map<Long, Long> issuedCounts = countIssued(drained);
        for (Coupon coupon : drained) {
            long issuedCount = issuedCounts.getOrDefault(coupon.getId(), 0L);
            Integer before = coupon.getIssuedQuantity();
            coupon.syncIssuedQuantity((int) issuedCount);
            couponRepository.save(coupon);
            log.info("쿠폰 정합성 동기화 완료 - couponId={}, issuedQuantity(전={}, 후={})",
                    coupon.getId(), before, issuedCount);
        }
    }

    private boolean isDrained(long couponId) {
        boolean drained = pendingChecker.isDrained(couponId);
        if (!drained) {
            log.info("정합성 동기화 대기 - Redis Stream에 미처리 건이 남아있음. couponId={}", couponId);
        }
        return drained;
    }

    private Map<Long, Long> countIssued(List<Coupon> coupons) {
        List<Long> couponIds = coupons.stream().map(Coupon::getId).toList();
        return couponIssueRepository.countGroupedByCouponIds(couponIds).stream()
                .collect(Collectors.toMap(
                        CouponIssueRepository.CouponIssueCount::getCouponId,
                        CouponIssueRepository.CouponIssueCount::getIssuedCount
                ));
    }

}
