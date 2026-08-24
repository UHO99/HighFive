package com.mycom.myapp.team5.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;

@SpringBootTest
public class CouponStatusServiceTest {
	@Autowired
	private CouponRepository couponRepository;
	
	@Autowired
	private CouponStatusService couponStatusService;
	
	@Autowired
	private StringRedisTemplate stringRedisTemplate;
	
	private final List<Long> createdCouponIds = new ArrayList<>();
	
	// 데이터 정리
	@AfterEach
	void tearDown() {
		createdCouponIds.forEach(id -> {
			stringRedisTemplate.delete(CouponStockKeys.stockKey(id));
			couponRepository.deleteById(id);
		});
		createdCouponIds.clear();
	}
	
	// 테스트 쿠폰 생성 (중복 방지)
	private long createReadyCoupon(int totalQuantity) {
		Coupon coupon = Coupon.builder()
				.name("상태-전환-테스트-쿠폰")
				.totalQuantity(totalQuantity)
				.startAt(LocalDateTime.now().minusMinutes(1))
				.endAt(LocalDateTime.now().plusDays(1))
				.build();				// status 는 기본 READY
		Coupon saved = couponRepository.save(coupon);
		createdCouponIds.add(saved.getId());
		return saved.getId();
	}
	
	// 테스트 5개
	// 1) READY -> OPEN 성공 + Redis 재고 초기화
	@Test
	public void READY_쿠폰_OPEN_성공시_Redis_재고가_초기화() {
		long couponId = createReadyCoupon(100);
		
		couponStatusService.openCoupon(couponId);
		
		assertThat(couponRepository.findById(couponId).orElseThrow().getStatus()).isEqualTo(CouponStatus.OPEN);
		assertThat(stringRedisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId))).isEqualTo(String.valueOf(100));
	}
	
	// 2) OPEN -> CLOSE 성공 + Redis 키 삭제
	@Test
	public void OPEN_쿠폰_CLOSE_성공시_Redis_재고_키_삭제() {
		long couponId = createReadyCoupon(100);
		couponStatusService.openCoupon(couponId);
		
		couponStatusService.closeCoupon(couponId);
		
		assertThat(couponRepository.findById(couponId).orElseThrow().getStatus()).isEqualTo(CouponStatus.CLOSE);
		assertThat(stringRedisTemplate.hasKey(CouponStockKeys.stockKey(couponId))).isFalse();
	}
	
	// 3) 동시 OPEN 10건 -> 성공 1건, 충돌 9건 (멱등성 핵심 테스트)
	@Test
	public void OPEN_10건_요청시_1건만_성공() throws InterruptedException{
		long couponId = createReadyCoupon(100);
		int threadCount = 10;
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		for (int i = 0; i < threadCount; i++) {
			executor.execute(() -> {
				try {
					couponStatusService.openCoupon(couponId);
					successCount.incrementAndGet();
				} catch (CouponException e) {
					if(e.getErrorCode() == CouponErrorCode.COUPON_STATUS_CONFLICT) {
						conflictCount.incrementAndGet();
					}
				}
			});
		}
		executor.shutdown();
		executor.awaitTermination(1, TimeUnit.MINUTES);
		
		assertThat(successCount.get()).isEqualTo(1);
		assertThat(conflictCount.get()).isEqualTo(9);
		assertThat(stringRedisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId))).isEqualTo(String.valueOf(100));
	}
	
	// 4) CLOSE 상태에서 OPEN 시도 -> (유스케이스 CP003)
	@Test
	public void CLOSE_상태에서_OPEN_시도_충돌_예외_발생() {
		long couponId = createReadyCoupon(100);
		couponStatusService.openCoupon(couponId);
		couponStatusService.closeCoupon(couponId);
		
		try {
			couponStatusService.openCoupon(couponId);
			fail("COUPON_STATUS_CONFLICT 예외가 발생해야 한다.");
		} catch (CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_STATUS_CONFLICT);
		}
	}
	
	// 5) 없는 쿠폰 -> (유스케이스 CP001)
	@Test
	public void 존재하지_않는_쿠폰_OPEN_시도시_NOT_FOUND_예외처리() {
		try {
			couponStatusService.openCoupon(999_999L);
			fail("COUPON_NOT_FOUND 예외가 발생해야 한다.");
		} catch(CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
		}
	}
}
