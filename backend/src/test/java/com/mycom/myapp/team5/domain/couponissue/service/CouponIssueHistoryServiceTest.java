package com.mycom.myapp.team5.domain.couponissue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryPage;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;

@ExtendWith(MockitoExtension.class)
class CouponIssueHistoryServiceTest {

	@Mock
	private CouponIssueRepository couponIssueRepository;
	@Mock
	private CouponRepository couponRepository;
	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private CouponIssueServiceImpl couponIssueService;

	@Test
	@DisplayName("시나리오7: 쿠폰별 발급 이력을 최근순으로 반환한다")
	void getIssuesByCouponId_success() {
		given(couponRepository.existsById(10L)).willReturn(true);

		CouponIssue issue1 = CouponIssue.builder().userId(1L).couponId(10L).build();
		ReflectionTestUtils.setField(issue1, "id", 100L);
		CouponIssue issue2 = CouponIssue.builder().userId(2L).couponId(10L).build();
		ReflectionTestUtils.setField(issue2, "id", 101L);

		Pageable pageable = PageRequest.of(0, 50);
		Page<CouponIssue> page = new PageImpl<>(List.of(issue2, issue1), pageable, 2);
		given(couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(10L, pageable)).willReturn(page);
		given(userRepository.findAllById(anyList())).willReturn(List.of());

		CouponIssueHistoryPage result = couponIssueService.getIssuesByCouponId(10L, 1, 50);

		assertThat(result.items()).hasSize(2);
		assertThat(result.items().get(0).issueId()).isEqualTo(101L);
		assertThat(result.items().get(0).userId()).isEqualTo(2L);
		assertThat(result.items().get(0).status()).isEqualTo(CouponIssueStatus.ISSUED);
		assertThat(result.items().get(1).userId()).isEqualTo(1L);
		assertThat(result.totalElements()).isEqualTo(2);
		assertThat(result.page()).isEqualTo(1);
	}

	@Test
	@DisplayName("시나리오7: 쿠폰이 없으면 CP001")
	void getIssuesByCouponId_notFound() {
		given(couponRepository.existsById(99L)).willReturn(false);

		assertThatThrownBy(() -> couponIssueService.getIssuesByCouponId(99L, 1, 50)).isInstanceOf(CouponException.class).extracting(ex -> ((CouponException) ex).getErrorCode()).isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);

		verify(couponIssueRepository, never()).findByCouponIdOrderByIssuedAtDesc(anyLong(), any());
	}

	@Test
	@DisplayName("시나리오7: 이력이 없으면 빈 리스트")
	void getIssuesByCouponId_empty() {
		given(couponRepository.existsById(10L)).willReturn(true);

		Pageable pageable = PageRequest.of(0, 50);
		given(couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(10L, pageable)).willReturn(Page.empty(pageable));

		assertThat(couponIssueService.getIssuesByCouponId(10L, 1, 50).items()).isEmpty();
	}
}