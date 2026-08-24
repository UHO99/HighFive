package com.mycom.myapp.team5.global.redis;

import lombok.NoArgsConstructor;

/**
 * Redis 원자적 재고 관리(CouponStockRedisService)에서 쓰는 키 이름 관리. 스트림(큐) 관련 키는 CouponStreamKeys가 별도로 관리한다.
 */
@NoArgsConstructor
public final class CouponStockKeys {

	private static final String STOCK_KEY_PREFIX = "coupon:stock:";
	private static final String ISSUED_SET_KEY_PREFIX = "coupon:issued:";
	private static final String FAIRNESS_SEQ_KEY_PREFIX = "coupon:fairness-seq:";
	private static final String FAIRNESS_LOG_KEY_PREFIX = "coupon:fairness-log:";

	public static String stockKey(long couponId) {
		return STOCK_KEY_PREFIX + couponId;
	}

	public static String issuedSetKey(long couponId) {
		return ISSUED_SET_KEY_PREFIX + couponId;
	}

	public static String fairnessSeqKey(long couponId) {
		return FAIRNESS_SEQ_KEY_PREFIX + couponId;
	}

	public static String fairnessLogKey(long couponId) {
		return FAIRNESS_LOG_KEY_PREFIX + couponId;
	}

}