package com.mycom.myapp.team5.domain.couponissue.service;

import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryResponse;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponIssueHistoryServiceTest {

    @Mock
    private CouponIssueRepository couponIssueRepository;
    @Mock
    private CouponRepository couponRepository;

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

        given(couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(10L))
                .willReturn(List.of(issue2, issue1));

        List<CouponIssueHistoryResponse> result = couponIssueService.getIssuesByCouponId(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).issueId()).isEqualTo(101L);
        assertThat(result.get(0).userId()).isEqualTo(2L);
        assertThat(result.get(0).status()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(result.get(1).userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("시나리오7: 쿠폰이 없으면 CP001")
    void getIssuesByCouponId_notFound() {
        given(couponRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> couponIssueService.getIssuesByCouponId(99L))
                .isInstanceOf(CouponException.class)
                .extracting(ex -> ((CouponException) ex).getErrorCode())
                .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);

        verify(couponIssueRepository, never()).findByCouponIdOrderByIssuedAtDesc(99L);
    }

    @Test
    @DisplayName("시나리오7: 이력이 없으면 빈 리스트")
    void getIssuesByCouponId_empty() {
        given(couponRepository.existsById(10L)).willReturn(true);
        given(couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(10L)).willReturn(List.of());

        assertThat(couponIssueService.getIssuesByCouponId(10L)).isEmpty();
    }
}
