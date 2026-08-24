package com.mycom.myapp.team5.domain.couponissue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.dto.MyCouponResponse;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;

@SpringBootTest
@Transactional
public class CouponIssueQueryTest {
	@Autowired
	private CouponIssueService couponIssueService;
	
	@Autowired
	private CouponIssueRepository couponIssueRepository;
	
	@Autowired
	private CouponRepository couponRepository;
	
	@Autowired
	private UserRepository userRepository;
	
	// 쿠폰(캠페인) 생성
	private long createCoupon(String name) {
		Coupon coupon = Coupon.builder()
				.name(name)
				.totalQuantity(100)
				.startAt(LocalDateTime.now().plusDays(1))
				.endAt(LocalDateTime.now().plusDays(2))
				.build();
		Coupon saved = couponRepository.save(coupon);
		return saved.getId();
	}
	
	// 발급 이력 생성 (상태 ISSUED, 발급 시각 = now)
	private long createIssue(long userId, long couponId) {
		CouponIssue issue = CouponIssue.builder()
				.userId(userId)
				.couponId(couponId)
				.build();
		CouponIssue saved = couponIssueRepository.save(issue);
		return saved.getId();
	}
	
	// 사용자 생성 (coupon_issue.user_id FK 대응)
	private long createUser(String email) {
		User saved = userRepository.save(User.builder().email(email).build());
		return saved.getId();
	}
	
	// 1) 목록 조회 - 여러 건, 최근 발급순 정렬 + 쿠폰명 매핑
	@Test
	public void 내_쿠폰_목록_최근발급순_정렬_및_쿠폰명_매핑() throws Exception {
		long pizzaCouponId = createCoupon("피자 쿠폰");
		long chickenCouponId = createCoupon("치킨 쿠폰");
		long userId = createUser("coupon1@test.com");
		
		CouponIssue pizzaIssue = CouponIssue.builder().userId(userId).couponId(pizzaCouponId).build();
		ReflectionTestUtils.setField(pizzaIssue, "issuedAt", LocalDateTime.now().minusDays(1));
		couponIssueRepository.save(pizzaIssue);
		createIssue(userId, chickenCouponId);			// 나중에 발급
		
		List<MyCouponResponse> result = couponIssueService.getMyCoupons(userId);
		
		assertThat(result).hasSize(2);
		assertThat(result.get(0).couponName()).isEqualTo("치킨 쿠폰");		// 최신이 먼저
		assertThat(result.get(1).couponName()).isEqualTo("피자 쿠폰");
		assertThat(result.get(0).status()).isEqualTo(CouponIssueStatus.ISSUED);
		assertThat(result.get(0).issueId()).isNotNull();
		assertThat(result.get(0).couponId()).isEqualTo(chickenCouponId);
	}
	
	// 2) 다른 사용자의 이력은 포함 X
	@Test
	public void 다른_사용자_쿠폰은_조회되지_않음() {
		long couponId = createCoupon("치킨 쿠폰");
		long user1 = createUser("u1@test.com");
		long user2 = createUser("u2@test.com");
		
		createIssue(user1, couponId);
		createIssue(user2, couponId);	  				// 다른 사용자
		
		List<MyCouponResponse> result = couponIssueService.getMyCoupons(user1);
		
		assertThat(result).hasSize(1);
		assertThat(result.get(0).couponName()).isEqualTo("치킨 쿠폰");
	}
	
	// 3) 발급 이력 없는 사용자 - 빈 목록(예외 처리 X)
	@Test
	public void 발급이력_없는_사용자_빈_목록() {
		long userId = createUser("noissue@test.com");			 // 발급 이력 없는 사용자
		List<MyCouponResponse> result = couponIssueService.getMyCoupons(userId);
		
		assertThat(result).isEmpty();
	}
	
	// 4) 단건 조회 - 본인 소유 성공
	@Test
	public void 본인_쿠폰_단건_조회_성공() {
		long couponId = createCoupon("치킨 쿠폰");
		long userId = createUser("coupon2@test.com");
		long issueId = createIssue(userId, couponId);
		
		MyCouponResponse result = couponIssueService.getMyCoupon(userId, issueId);
		
		assertThat(result.issueId()).isEqualTo(issueId);
		assertThat(result.couponName()).isEqualTo("치킨 쿠폰");
		assertThat(result.status()).isEqualTo(CouponIssueStatus.ISSUED);
	}
	
	// 5) 단건 조회 - 타인 소유 -> CI002 (존재 여부 노출 금지)
	@Test
	public void 타인_쿠폰_단건_조회_실패() {
		long couponId = createCoupon("치킨 쿠폰");
		long ownerId = createUser("owner@test.com");   		// 소유자
		long userId = createUser("coupon3@test.com");		// 조회자
		long issueId = createIssue(ownerId, couponId);
		
		try {
			couponIssueService.getMyCoupon(userId, issueId);		// 실제 타인 id
			fail("CI002 예외 발생");			
		} catch(CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_ISSUE_NOT_FOUND);
		}
	}
	
	// 6) 단건 조회 - 없는 issueId -> CI002
	@Test
	public void 없는_이력_단건_조회_실패() {
	    long couponId = createCoupon("치킨 쿠폰");
	    long userId = createUser("coupon6@test.com");
	    long issueId = createIssue(userId, couponId);
	    couponIssueRepository.deleteById(issueId);   // 존재하던 이력 삭제 → "없는 이력" 상태

		try {
			couponIssueService.getMyCoupon(userId, issueId);
			fail("CI002 예외 발생");
		} catch(CouponException e) {
			assertThat(e.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_ISSUE_NOT_FOUND);
		}
	}
}
