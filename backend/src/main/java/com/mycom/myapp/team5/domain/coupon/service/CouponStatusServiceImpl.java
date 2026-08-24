package com.mycom.myapp.team5.domain.coupon.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.event.CouponOpenedEvent;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponStatusServiceImpl implements CouponStatusService {

	private final CouponRepository couponRepository;
	private final CouponStockRedisService couponStockRedisService; // 기존 코드 재사용
	private final StringRedisTemplate stringRedisTemplate; // CLOSE 시 키 삭제
	private final ApplicationEventPublisher eventPublisher; // OPEN 시 Redis Stream 구독 즉시 트리거

	@Override
	@Transactional
	public void openCoupon(long couponId) {
		// 1) 비관적 락 조회 -> 동시 open 요청이 직렬화되어 한 번만 반영하여 멱등성 보장
		Coupon coupon = couponRepository.findByIdForUpdate(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		// 2) 전이 규칙 검증 : READY가 아니면 409
		if (!coupon.getStatus().canTransitTo(CouponStatus.OPEN)) {
			throw new CouponException(CouponErrorCode.COUPON_STATUS_CONFLICT);
		}

		// 3) 상태 변경 (dirty checking -> 트랜잭션 커밋 시 UPDATE)
		coupon.open();

		// 4) 발급 이력 SET을 먼저 비운다 - closeCoupon이 정상적으로 지웠다면 원래 비어있지만,
		// 이 정리 로직이 생기기 전에 CLOSE된 쿠폰과 id가 겹치는 경우까지 방어한다.
		stringRedisTemplate.delete(CouponStockKeys.issuedSetKey(couponId));

		// 5) Redis 재고 초기화 : 기존 initStock() 재사용
		couponStockRedisService.initStock(couponId, coupon.getTotalQuantity());

		couponStockRedisService.resetFairnessLog(couponId); // 추가

		// 6) Redis Stream 구독 즉시 트리거
		eventPublisher.publishEvent(new CouponOpenedEvent(couponId));
	}

	@Override
	@Transactional
	public void closeCoupon(long couponId) {
		Coupon coupon = couponRepository.findByIdForUpdate(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

		if (!coupon.getStatus().canTransitTo(CouponStatus.CLOSE)) {
			throw new CouponException(CouponErrorCode.COUPON_STATUS_CONFLICT);
		}

		coupon.close();

		// CLOSE 시 재고 키 정리 (발급 파트가 더 이상 차감하지 못하도록).
		// 발급 이력 SET(issuedSetKey)도 같이 지운다 - 안 지우면, 나중에 같은 id를 재사용하는 새
		// 쿠폰(예: 더미데이터 재적재로 coupon 테이블이 TRUNCATE돼서 auto_increment가 리셋된 뒤
		// 생성된 쿠폰)이 이 쿠폰의 leftover 발급 기록을 그대로 물려받는다 - 방금 오픈했는데 Redis
		// 발급 건수가 0이 아니게 보이는 원인이 이거였다.
		stringRedisTemplate.delete(CouponStockKeys.stockKey(couponId));
		stringRedisTemplate.delete(CouponStockKeys.issuedSetKey(couponId));
	}

	@Override
	public CouponStatus getStatus(long couponId) {
		return couponRepository.findById(couponId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND)).getStatus();
	}

}
