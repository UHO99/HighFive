package com.mycom.myapp.team5.domain.coupon.service;

import com.mycom.myapp.team5.global.common.enums.CouponStatus;

public interface CouponStatusService {

	/**
	 * READY -> OPEN 전환 + Redis 재고 초기화
	 * (수동 API와 스케줄러가 공용으로 호출)
	 */
	void openCoupon(long couponId);
	
	/**
	 * OPEN -> CLOSE 전환 + Redis 재고 키 정리
	 */
	void closeCoupon(long couponId);
	
	/**
	 * 현재 상태 조회
	 */
	CouponStatus getStatus(long couponId);
}
