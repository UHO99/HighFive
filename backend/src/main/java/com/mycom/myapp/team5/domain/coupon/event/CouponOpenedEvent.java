package com.mycom.myapp.team5.domain.coupon.event;

/**
 * 쿠폰이 OPEN으로 전이된 직후(트랜잭션 커밋 후) 발행된다.
 * Redis Stream 구독을 "다음 폴링까지 대기"가 아니라 그 즉시 등록하기 위한 트리거 - 트래픽이
 * 제일 몰리는 순간이 하필 이 시점이라, 구독이 늦게 걸리면 그 지연 구간의 요청이 뒤늦게 처리된다.
*/
public record CouponOpenedEvent(long couponId) { }
