package com.mycom.myapp.team5.domain.coupon.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;

@SpringBootTest
public class CouponStatusSchedulerTest {
	@Autowired
	private CouponRepository couponRepository;
	
	@Autowired
	private CouponStatusService couponStatusService;			// OPEN 상태 쿠폰 준비용
	
	@Autowired
	private CouponStatusScheduler couponStatusScheduler;		//테스트 대상 (메서드 직접 호출)
	
	@Autowired
	private StringRedisTemplate stringRedisTemplate;
	
	@Autowired
	private CouponIssueRepository couponIssueRepository;
	
	@Autowired
	private UserRepository userRepository;
	
	private final List<Long> createdCouponIds = new ArrayList<>();
	private final List<Long> createdIssueIds = new ArrayList<>();
	private final List<Long> createdUserIds = new ArrayList<>();
	
	// 데이터 정리 (Redis 키 + 쿠폰 row)
	@AfterEach
	void tearDown() {
		createdIssueIds.forEach(couponIssueRepository::deleteById);
		createdCouponIds.forEach(id -> {
			stringRedisTemplate.delete(CouponStockKeys.stockKey(id));
			couponRepository.deleteById(id);
		});
		createdUserIds.forEach(userRepository::deleteById);
		createdIssueIds.clear();
		createdCouponIds.clear();
		createdUserIds.clear();
	}
	
	// 테스트 쿠폰 생성 (startAt/endAt 지정 가능, status 는 기본 READY)
	private long createCoupon(int totalQuantity, LocalDateTime startAt, LocalDateTime endAt) {
		Coupon coupon = Coupon.builder()
				.name("스케줄러-테스트-쿠폰")
				.totalQuantity(totalQuantity)
				.startAt(startAt)
				.endAt(endAt)
				.build();
		Coupon saved = couponRepository.save(coupon);
		createdCouponIds.add(saved.getId());
		return saved.getId();
	}
	
	// 발급 이력 생성 (coupon_issue.coupon_id FK 대응)
	private long createIssue(long userId, long couponId) {
		CouponIssue issue = CouponIssue.builder()
				.userId(userId)
				.couponId(couponId)
				.build();
		CouponIssue saved = couponIssueRepository.save(issue);
		createdIssueIds.add(saved.getId());
		return saved.getId();
	}
	
	// 사용자 생성 (coupon_issue.user_id FK 대응)
	private long createUser(String email) {
		User user = userRepository.save(User.builder().email(email).build());
		createdUserIds.add(user.getId());
		return user.getId();
	}
	
	// 1) startAt이 지난 READY 쿠폰을 autoOpen()이 OPEN 으로 전환 + Redis 재고 초기화 (유스케이스 S010)
	@Test
	public void autoOpen_실행시_시작시간이_지난_READY_쿠폰_OPEN_변경_재고초기화() {
		long couponId = createCoupon(100, 
				LocalDateTime.now().minusMinutes(1),			// startAt 지남
				LocalDateTime.now().plusDays(1)
				);
		
		couponStatusScheduler.autoOpen();
		
		assertThat(couponRepository.findById(couponId).orElseThrow().getStatus()).isEqualTo(CouponStatus.OPEN);
		assertThat(stringRedisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId))).isEqualTo(String.valueOf(100));
	}
	
	// 2) endAt이 지난 OPEN 쿠폰을 autoClose()가 CLOSE 로 전환 + Redis 키 삭제 (유스케이스 S011)
	@Test
	public void autoClose_실행시_종료시간이_지난_OPEN_쿠폰_CLOSE_변경_키삭제() {
		long couponId = createCoupon(100,
				LocalDateTime.now().minusMinutes(2),
				LocalDateTime.now().minusMinutes(1)			// endAt 지남
				);
		couponStatusService.openCoupon(couponId); 			// OPEN 상태로 준비 (Redis 재고 초기화 포함)
		
		couponStatusScheduler.autoClose();
		
		assertThat(couponRepository.findById(couponId).orElseThrow().getStatus()).isEqualTo(CouponStatus.CLOSE);
		assertThat(stringRedisTemplate.hasKey(CouponStockKeys.stockKey(couponId))).isFalse();
	}
	
	// 3) startAt이 미래인 READY 쿠폰은 autoOpen()이 건드리지 X
	@Test
	public void autoOpen_실행시_시작시간이_나중이면_쿠폰은_READY() {
		long couponId = createCoupon(100,
				LocalDateTime.now().plusDays(1),			// startAt 미래
				LocalDateTime.now().plusDays(2));
		
		couponStatusScheduler.autoOpen();
		
		assertThat(couponRepository.findById(couponId).orElseThrow().getStatus()).isEqualTo(CouponStatus.READY);
		assertThat(stringRedisTemplate.hasKey(CouponStockKeys.stockKey(couponId))).isFalse();
	}
	
	// 4) OPEN 인데 Redis 재고 키가 없으면 replenishMissingStock()이 재적재 (Redis 재시작 대응)
	@Test
	public void replenishMissingStock_실행시_OPEN_쿠폰_재고_키_복구() {
		long couponId = createCoupon(100,
				LocalDateTime.now().minusMinutes(1),
				LocalDateTime.now().plusDays(1));
		couponStatusService.openCoupon(couponId); 			// OPEN + 재고 초기화
		stringRedisTemplate.delete(CouponStockKeys.stockKey(couponId));			// Redis 재시작 상황 재현
		
		couponStatusScheduler.replenishMissingStock();
		
		assertThat(stringRedisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId))).isEqualTo(String.valueOf(100));
	}
	
	// 5) 발급 일부 진행 후 Redis 재시작 -> 남은 재고(total - 발급 건수)로 복구 (초과 발급 방지)
	@Test
	public void replenishMissingStock_실행시_발급된_수량_차감_복구() {
		long couponId = createCoupon(100,
				LocalDateTime.now().minusMinutes(1),
				LocalDateTime.now().plusDays(1));
		couponStatusService.openCoupon(couponId); 					// OPEN + 재고 100 초기화
		for (int i = 0; i < 30; i++) {								// 발급 30건 재현
			long userId = createUser("replenish-" + i + "@test.com");
			createIssue(userId, couponId);
		}
		stringRedisTemplate.delete(CouponStockKeys.stockKey(couponId)); 			// Redis 재시작 상황 재현
		
		couponStatusScheduler.replenishMissingStock();
		
		assertThat(stringRedisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId)))
		.isEqualTo("70");					// 100 - 30 (기존 로직이면 "100"이라 실패)
	}
}
