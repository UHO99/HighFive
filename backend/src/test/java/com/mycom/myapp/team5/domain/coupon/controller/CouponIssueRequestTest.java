package com.mycom.myapp.team5.domain.coupon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer;

@SpringBootTest
public class CouponIssueRequestTest {
	@Autowired
	private CouponService couponService;
	
	@Autowired
	private CouponStatusService couponStatusService;
	
	// 실제 발급 파이프라인은 mock -> 컨슈머 (100ms 주기)의 비동기 DB INSERT 간섭을 막아 테스트를 결정적으로 만듦.
	@MockitoBean
	private CouponIssueStreamProducer producer;
	
	@Autowired
	private CouponRepository couponRepository;
	
	private final List<Long> createdCouponIds = new ArrayList<>();
	
	// 데이터 정리
	@AfterEach
	void tearDown() {
		createdCouponIds.forEach(couponRepository::deleteById);
		createdCouponIds.clear();
	}
	
	// 테스트 쿠폰 생성 (status 기본 READY)
	private long createCoupon() {
		Coupon coupon = Coupon.builder()
				.name("발급-검증-테스트-쿠폰")
				.totalQuantity(100)
				.startAt(LocalDateTime.now().minusMinutes(1))
				.endAt(LocalDateTime.now().plusDays(1))
				.build();
		Coupon saved = couponRepository.save(coupon);
		createdCouponIds.add(saved.getId());
		return saved.getId();
	}
	
	// 1) OPEN 상태 쿠폰 -> 검증 통과 (예외 없음)
	@Test
	public void OPEN_쿠폰_발급_검증_통과() {
		long couponId = createCoupon();
		couponStatusService.openCoupon(couponId);			// READY -> OPEN (Redis 재고 초기화 포함)
		
		assertDoesNotThrow(() -> couponService.validateIssueable(couponId));
	}
	
	
	// 2) READY 상태 쿠폰 -> COUPON_NOT_OPEN(CP002)
	@Test
	public void READY_쿠폰_발급_검증_실패() {
		long couponId = createCoupon();
		
		try {
			couponService.validateIssueable(couponId);
			fail("COUPON_NOT_OPEN 예외 발생");
		} catch (CouponException e) {
			// 400 + CP002
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_NOT_OPEN);
		}
	}
	
	// 3) CLOSE 상태 쿠폰 -> COUPON_NOT_OPEN(CP002)
	@Test
	public void CLOSE_쿠폰_발급_검증_실패() {
		long couponId = createCoupon();
		couponStatusService.openCoupon(couponId);  			// OPEN 전환
		couponStatusService.closeCoupon(couponId); 			// CLOSE 전환
		
		try {
			couponService.validateIssueable(couponId);
			fail("COUPON_NOT_OPEN 예외 발생");
		} catch(CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_NOT_OPEN);
		}
	}
	
	// 4) 존재하지 않는 쿠폰 -> COUPON_NOT_FOUND(CP001)
	@Test
	public void 미존재_쿠폰_발급_검증_실패() {
		try {
			couponService.validateIssueable(999_999L);
			fail("COUPON_NOT_FOUN 예외 발생");
		} catch(CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
		}
	}
}
