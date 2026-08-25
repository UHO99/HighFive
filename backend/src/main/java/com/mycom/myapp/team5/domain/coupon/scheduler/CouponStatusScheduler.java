package com.mycom.myapp.team5.domain.coupon.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 유스케이스 S010/S011 - 등록된 발급 기간(startAt/endAt) 에 맞춰 쿠폰 상태를 자동 전환
 * 수동 API(open/close)와 동일한 CouponStatusService를 호출하므로 로직은 단일화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStatusScheduler {

	private final CouponRepository couponRepository;
	private final CouponStatusService couponStatusService;
	private final CouponStockRedisService couponStockRedisService;
	private final StringRedisTemplate stringRedisTemplate;
	private final CouponIssueRepository couponIssueRepository;
	
	// (유스케이스 S010) startAt이 지난 READY 쿠폰을 자동 OPEN (Redis 재고 초기화 포함)
	@Scheduled(fixedDelay = 60_000)
	public void autoOpen() {
		LocalDateTime now = LocalDateTime.now();
		List<Coupon> coupons = couponRepository.findByStatusAndStartAtLessThanEqual(CouponStatus.READY, now);
		
		for(Coupon coupon : coupons) {
			try {
				couponStatusService.openCoupon(coupon.getId());
				log.info("쿠폰 자동 OPEN - couponId = {}", coupon.getId());
			} catch (Exception e) {
				// 이미 OPEN 됐거나(멱등) 다른 이유로 실패해도 다음 주기에 재시도
				log.warn("쿠폰 자동 OPEN 실패(다음 주기 재시도) - couponId = {}, reason = {}", coupon.getId(), e.getMessage());
			}
		}
	}
	
	// (유스케이스 S011) endAt이 지난 OPEN 쿠폰을 자동 CLOSE (Redis 키 정리 포함)
	@Scheduled(fixedDelay = 60_000)
	public void autoClose() {
		LocalDateTime now = LocalDateTime.now();
		List<Coupon> coupons = couponRepository.findByStatusAndEndAtLessThanEqual(CouponStatus.OPEN, now);
		
		for(Coupon coupon : coupons) {
			try {
				couponStatusService.closeCoupon(coupon.getId());
				log.info("쿠폰 자동 CLOSE - couponId = {}", coupon.getId());
			} catch (Exception e) {
				// 이미 CLOSE 됐거나(멱등) 다른 이유로 실패해도 다음 주기에 재시도
				log.warn("쿠폰 자동 CLOSE 실패(다음 주기 재시도) - couponId = {}, reason = {}", coupon.getId(), e.getMessage());
			}
		}
	}
	
	// status=OPEN 인데 Redis 재고 키가 없으면 재적재 (Redis 재시작 대응)
	@Scheduled(fixedDelay = 60_000)
	public void replenishMissingStock() {
		List<Coupon> openCoupons = couponRepository.findByStatus(CouponStatus.OPEN);

        for (Coupon coupon : openCoupons) {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(CouponStockKeys.stockKey(coupon.getId())))) {
            	// 발급 건수를 차감해 복구해야 초과 발급을 막는다 (OPEN은 issuedQuantity 가 null -> 실측 카운트)
            	// ISSUED 상태만 카운트하여 CANCELED/EXPIRED는 재고에 영향 X
            	long issued = coupon.getIssuedQuantity() != null 
            			? coupon.getIssuedQuantity() 
            			: couponIssueRepository.countByCouponIdAndStatus(coupon.getId(), CouponIssueStatus.ISSUED);
                int remaining = (int) Math.max(0L, coupon.getTotalQuantity() - issued);
                couponStockRedisService.initStock(coupon.getId(), remaining);
                log.info("Redis 재고 보정 완료 - couponId={}, remaining={}", coupon.getId(), remaining);
            }
        }
	}
}
