package com.mycom.myapp.team5.domain.couponissue.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CouponIssueUseCancelConcurrencyTest {
	@Autowired
	private CouponIssueService couponIssueService;
	
	@Autowired
	private CouponIssueRepository couponIssueRepository;
	
	@Autowired
	private CouponRepository couponRepository;
	
	@Autowired
	private UserRepository userRepository;
	
	private final List<Long> createdCouponIds = new ArrayList<>();
	private final List<Long> createdIssueIds = new ArrayList<>();
	private final List<Long> createdUserIds = new ArrayList<>();
	
	@AfterEach
	void tearDown() {
		try {
			createdIssueIds.forEach(couponIssueRepository::deleteById);
		} catch (Exception ignored) {}
		try{
			createdCouponIds.forEach(couponRepository::deleteById);
		} catch (Exception ignored) {}
		try {
			createdUserIds.forEach(userRepository::deleteById);			
		} catch (Exception ignored) {}
		
		createdIssueIds.clear();
		createdCouponIds.clear();
		createdUserIds.clear();
	}
	
	// 테스트 쿠폰 생성 (OPEN 상태, 즉시 발급 가능)
	private long createCoupon(int totalQuantity) {
		Coupon coupon = Coupon.builder()
				.name("동시성-테스트-쿠폰" + System.nanoTime())
				.totalQuantity(totalQuantity)
				.startAt(LocalDateTime.now().minusMinutes(1))
				.endAt(LocalDateTime.now().plusDays(1))
				.build();
		Coupon saved = couponRepository.save(coupon);
		createdCouponIds.add(saved.getId());
		return saved.getId();
	}
	
	// 사용자 생성
	private long createUser(String prefix) {
		String email = prefix + "-" + System.nanoTime() + "@test.com";
		User saved = userRepository.save(User.builder().email(email).build());
		createdUserIds.add(saved.getId());
		return saved.getId();
	}
	
	// 발급 이력 생성 (ISSUED 상태)
	private long createIssue(long userId, long couponId) {
		CouponIssue issue = CouponIssue.builder()
				.userId(userId)
				.couponId(couponId)
				.build();
		CouponIssue saved = couponIssueRepository.save(issue);
		createdIssueIds.add(saved.getId());
		return saved.getId();
	}
	
	// 1) 동시 사용 요청 - 1건만 성공, 나머지는 CI003
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void 동시_사용_요청시_1건만_성공_나머지_CI003() throws InterruptedException {
		long couponId = createCoupon(100);
		long userId = createUser("concurrent-use");
		long issueId = createIssue(userId, couponId);
		
		int threadCount = 10;
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			for(int i = 0; i < threadCount; i++) {
				executor.execute(() -> {
					try {
						couponIssueService.useCoupon(userId, issueId);
						successCount.incrementAndGet();
					} catch (CouponException e) {
						if(e.getErrorCode() == CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT) {
							conflictCount.incrementAndGet();
						}else {
							throw e;
						}
					}
				});
			}			
		} finally {
			executor.shutdown();
			try {
				if(!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch(InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
			
			if(executor instanceof AutoCloseable) {
				try {((AutoCloseable) executor).close();} catch(Exception ignored) {}
			}
		}
		
		assertThat(successCount.get()).isEqualTo(1);
		assertThat(conflictCount.get()).isEqualTo(9);
	}
	
	// 2) 동시 취소 요청 - 1건만 성공, 나머지는 CI003
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void 동시_취소_요청시_1건만_성공_나머지_CI003() throws InterruptedException{
		long couponId = createCoupon(100);
		long userId = createUser("concurrent-cancel");
		long issueId = createIssue(userId, couponId);
		
		int threadCount = 10;
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			for(int i = 0; i < threadCount; i++) {
				executor.execute(() -> {
					try {
						couponIssueService.cancelCoupon(userId, issueId);
						successCount.incrementAndGet();
					}catch(CouponException e) {
						if(e.getErrorCode() == CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT) {
							conflictCount.incrementAndGet();
						} else {
							throw e;
						}
					}
				});
			}
		} finally {
			executor.shutdown();
			try {
				if(!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
			
			if(executor instanceof AutoCloseable) {
				try {((AutoCloseable) executor).close();} catch(Exception ignored) {}
			}
		}
		
		assertThat(successCount.get()).isEqualTo(1);
		assertThat(conflictCount.get()).isEqualTo(9);
	}
}
