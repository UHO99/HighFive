package com.mycom.myapp.team5.domain.couponissue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;

@SpringBootTest
public class CouponIssueUseCancelTest {
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
		createdIssueIds.forEach(couponIssueRepository::deleteById);
		createdCouponIds.forEach(couponRepository::deleteById);
		createdUserIds.forEach(userRepository::deleteById);
		createdIssueIds.clear();
		createdCouponIds.clear();
		createdUserIds.clear();
	}
	
	private long createCoupon(int totalQuantity) {
		Coupon coupon = Coupon.builder()
				.name("사용취소-테스트-쿠폰-" + System.nanoTime())
				.totalQuantity(totalQuantity)
				.startAt(LocalDateTime.now().minusMinutes(1))
				.endAt(LocalDateTime.now().plusDays(1))
				.build();
		Coupon saved = couponRepository.save(coupon);
		createdCouponIds.add(saved.getId());
		return saved.getId();
	}
	
	private long createUser(String prefix) {
		String email = prefix + "-" + System.nanoTime() + "@test.com";
		User saved = userRepository.save(User.builder().email(email).build());
		createdUserIds.add(saved.getId());
		return saved.getId();
	}
	
	private long createIssue(long userId, long couponId) {
		CouponIssue issue = CouponIssue.builder()
				.userId(userId)
				.couponId(couponId)
				.build();
		CouponIssue saved = couponIssueRepository.save(issue);
		createdIssueIds.add(saved.getId());
		return saved.getId();
	}
	
	// 1) 사용 성공 - ISSUED -> USED
	@Test
	public void 사용_성공_ISSUED에서_USED_전이() {
		long couponId = createCoupon(100);
		long userId = createUser("use-success");
		long issueId = createIssue(userId, couponId);
		
		couponIssueService.useCoupon(userId, issueId);
		
		CouponIssue updated = couponIssueRepository.findById(issueId).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(CouponIssueStatus.USED);
		assertThat(updated.getUsedAt()).isNotNull();
	}
	
	// 2) 이미 사용된 쿠폰 재사용 -> CI003
	@Test
	public void 이미_사용된_쿠폰_재사용시_CI003() {
		long couponId = createCoupon(100);
		long userId = createUser("use-duplicate");
		long issueId = createIssue(userId, couponId);
		
		couponIssueService.useCoupon(userId, issueId);
		
		try {
			couponIssueService.useCoupon(userId, issueId);
			fail("CI003 예외 발생");
		} catch (CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT);
		}
	}
	
	// 3) 취소된 쿠폰 사용 시도 -> CI003
	@Test
	public void 취소된_쿠폰_사용시_CI003() {
		long couponId = createCoupon(100);
		long userId = createUser("use-canceled");
		long issueId = createIssue(userId, couponId);
		
		CouponIssue issue = couponIssueRepository.findById(issueId).orElseThrow();
		issue.cancel();
		
		try {
			couponIssueService.useCoupon(userId, issueId);
			fail("CI003 예외 발생");
		} catch(CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT);
		}
	}
	
	// 4) 타인 쿠폰 사용 -> CI002 (소유권 검증)
	@Test
	public void 타인_쿠폰_사용시_CI002() {
		long couponId = createCoupon(100);
		long ownerId = createUser("owner");
		long userId = createUser("other-user");
		long issueId = createIssue(ownerId, couponId);
		
		try {
			couponIssueService.useCoupon(userId, issueId);
			fail("CI002 예외 발생");
		} catch(CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_ISSUE_NOT_FOUND);
		}
	}
	
	// 5) 취소 성공 - ISSUED -> CANCELED
	@Test
	public void 취소_성공_ISSUED에서_CANCELED로_전이() {
		long couponId = createCoupon(100);
		long userId = createUser("cancel-success");
		long issueId = createIssue(userId, couponId);
		
		couponIssueService.cancelCoupon(userId, issueId);
		
		CouponIssue updated = couponIssueRepository.findById(issueId).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(CouponIssueStatus.CANCELED);
		assertThat(updated.getCanceledAt()).isNotNull();
	}
}