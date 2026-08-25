package com.mycom.myapp.team5.benchmark.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

@SpringBootTest
public class RedisBatchSyncTest {

	private static final int REQUEST_COUNT = 500;

	@Autowired
	private CouponRepository couponRepository;

	@Autowired
	private CouponIssueRepository couponIssueRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CouponIssueStreamProducer couponIssueStreamProducer;

	@Autowired
	private CouponStockRedisService couponStockRedisService;

	private final List<Long> createdCouponIds = new ArrayList<>();
	private List<Long> userIds;

	// coupon_issue.user_id 가 users.id 를 FK로 참조하므로, 실제 유저를 먼저 만들어둬야 발급 이력이 저장된다.
	@BeforeEach
	void seedUsers() {
		userIds = new ArrayList<>(REQUEST_COUNT);
		for (int i = 0; i < REQUEST_COUNT; i++) {
			User user = userRepository.save(User.builder().email("redis-test-" + UUID.randomUUID() + "@test.com").build());
			userIds.add(user.getId());
		}
	}

	@AfterEach
	void tearDown() {
		// FK 순서상 coupon_issue -> coupon -> users 순으로 지워야 한다.
		createdCouponIds.forEach(couponIssueRepository::deleteByCouponId);
		createdCouponIds.forEach(couponRepository::deleteById);
		createdCouponIds.clear();
		userRepository.deleteAllById(userIds);
	}

	@Test
	public void CLOSE되고_스트림이_드레인된_쿠폰만_정합성_동기화된다() throws InterruptedException {
		// given: 쿠폰 두 개를 동시에 OPEN해서, 쿠폰별로 나뉜 스트림이 서로 섞이지 않는지도 같이 검증한다.
		long stillOpenCouponId = createOpenCoupon("계속-열려있는-쿠폰");
		long closingCouponId = createOpenCoupon("곧-닫힐-쿠폰");

		issueRequests(stillOpenCouponId);
		issueRequests(closingCouponId);

		awaitIssuedHistoryCount(stillOpenCouponId, REQUEST_COUNT);
		awaitIssuedHistoryCount(closingCouponId, REQUEST_COUNT);

		// when: 하나만 CLOSE 처리
		Coupon closing = couponRepository.findById(closingCouponId).orElseThrow();
		closing.close();
		couponRepository.save(closing);

		// then: CLOSE된 쿠폰만 S012 배치가 issuedQuantity를 채운다
		await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
			Coupon result = couponRepository.findById(closingCouponId).orElseThrow();
			assertThat(result.getIssuedQuantity()).isEqualTo(REQUEST_COUNT);
		});

		// 여전히 OPEN인 쿠폰은 동기화 대상이 아니므로 null 그대로여야 한다
		Coupon stillOpen = couponRepository.findById(stillOpenCouponId).orElseThrow();
		assertThat(stillOpen.getIssuedQuantity()).isNull();
	}

	private long createOpenCoupon(String name) {
		Coupon coupon = Coupon.builder().name(name).totalQuantity(REQUEST_COUNT).startAt(LocalDateTime.now().minusMinutes(1)).endAt(LocalDateTime.now().plusDays(1)).build();
		coupon.open();
		coupon = couponRepository.save(coupon);
		createdCouponIds.add(coupon.getId());
		// couponIssueStreamProducer.requestIssue()가 발급 전 Redis 재고를 확인하므로
		// 실제 서비스(CouponStatusServiceImpl.openCoupon())가 하는 initStock()을 여기서도 해줘야 한다.
		couponStockRedisService.initStock(coupon.getId(), REQUEST_COUNT);
		return coupon.getId();
	}

	private void issueRequests(long couponId) throws InterruptedException {
		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		for (long userId : userIds) {
			executorService.execute(() -> couponIssueStreamProducer.requestIssue(couponId, userId, System.currentTimeMillis()));
		}
		executorService.shutdown();
		executorService.awaitTermination(1, TimeUnit.MINUTES);
	}

	private void awaitIssuedHistoryCount(long couponId, int expected) {
		await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> assertThat(couponIssueRepository.countByCouponId(couponId)).isEqualTo((long) expected));
	}

}
