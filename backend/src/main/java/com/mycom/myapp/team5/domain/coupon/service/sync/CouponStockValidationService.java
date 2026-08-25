package com.mycom.myapp.team5.domain.coupon.service.sync;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.config.RedisStreamConfig;
import com.mycom.myapp.team5.global.redis.CouponStreamKeys;
import com.mycom.myapp.team5.global.redis.CouponStreamPendingChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S013 - CLOSE된 쿠폰의 재고<->발급 이력 정합성을 주기적으로 재검증한다.
 * S012와 달리 이미 동기화된 쿠폰도 매번 다시 검증한다 - "한 번 맞았으니 계속 맞다"고 가정하지 않는다.
 * 단, 한 번 "드레인 완료 + 기록값=실측값"이 확인된 쿠폰은 coupon_issue가 더 이상 늘어날 일이 없으므로
 * consistencyConfirmedAt을 채워 재검증 대상에서 영구히 빼고, 그 시점에 Redis Stream도 함께 정리한다.
 * 이렇게 하지 않으면 CLOSE 누적 건수만큼 매 사이클 부하가 무한정 커진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStockValidationService {

    public static final long INTERVAL_MS = 60_000;

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final CouponStreamPendingChecker pendingChecker;
    private final RedisStreamConfig redisStreamConfig;
    private final MismatchNotifier mismatchNotifier;
    private final CouponConsistencyStatusHolder statusHolder;

    // (검증 대상) CLOSE된 쿠폰만 본다 - OPEN 중에는 coupon_issue가 계속 늘어나는 중이라
    // "실측값 == 기록값" 비교 자체가 의미가 없다. 그래서 OPEN 쿠폰은 여기 targets에 절대 안 잡힌다.
    @Scheduled(fixedDelay = INTERVAL_MS)
    public void verifyClosedCoupons() {
        Instant now = Instant.now();
        List<Coupon> targets = couponRepository.findByStatusAndConsistencyConfirmedAtIsNull(CouponStatus.CLOSE);
        if (targets.isEmpty()) {
            statusHolder.updateVerify(now, 0, 0, 0);
            statusHolder.recordMismatchCycle(now, List.of());
            return;
        }

        Map<Long, Long> issuedCounts = countIssued(targets);

        List<CouponMismatchReport> mismatches = new ArrayList<>();
        for (Coupon coupon : targets) {
            verify(coupon, issuedCounts.getOrDefault(coupon.getId(), 0L), mismatches);
        }

        log.info("S013 정합성 검증 배치 실행 완료 - 대상={}건, 불일치={}건", targets.size(), mismatches.size());
        statusHolder.updateVerify(now, targets.size(), targets.size() - mismatches.size(), mismatches.size());
        statusHolder.recordMismatchCycle(now, mismatches);
    }

    private void verify(Coupon coupon, long actualIssuedCount, List<CouponMismatchReport> mismatches) {
        long pendingCount = pendingChecker.pendingCount(coupon.getId());

        CouponMismatchReport report = new CouponMismatchReport(
                coupon.getId(), coupon.getIssuedQuantity(), actualIssuedCount, pendingCount
        );

        if (report.isMismatch()) {
            mismatchNotifier.notify(report);
            mismatches.add(report);
            return;
        }

        confirmAndRetire(coupon);
    }

    // 드레인 완료 + 기록값=실측값이 처음 확인된 시점 - 스트림 정리가 실제로 성공했을 때만
    // consistencyConfirmedAt을 채운다. 순서를 반대로 하면(먼저 확정 저장 후 정리 시도) 정리가
    // 보류되는 경우(retireStream()이 false 반환) 그 쿠폰이 재검증 대상에서 이미 빠진 뒤라
    // 아무도 다시 확인하지 않는 채로 방치된다.
    private void confirmAndRetire(Coupon coupon) {
        boolean retired = redisStreamConfig.retireStream(CouponStreamKeys.streamKey(coupon.getId()));
        if (!retired) {
            return;
        }
        Instant confirmedAt = Instant.now();
        coupon.confirmConsistency(LocalDateTime.now());
        couponRepository.save(coupon);
        statusHolder.recordVerifyConfirmation(coupon.getId(), confirmedAt);
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
