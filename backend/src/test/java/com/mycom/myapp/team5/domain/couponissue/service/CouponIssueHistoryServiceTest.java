package com.mycom.myapp.team5.domain.couponissue.service;

import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryResponse;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponIssueHistoryServiceTest {

    @Mock
    private CouponIssueRepository couponIssueRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CouponStockRedisService couponStockRedisService;

    @InjectMocks
    private CouponIssueServiceImpl couponIssueService;

    @Test
    @DisplayName("시나리오7: 쿠폰별 발급 이력을 최근순으로 반환하고 개인정보를 마스킹한다")
    void getIssuesByCouponId_success() {
        given(couponRepository.existsById(10L)).willReturn(true);

        CouponIssue issue1 = CouponIssue.builder().userId(1L).couponId(10L).build();
        ReflectionTestUtils.setField(issue1, "id", 100L);
        CouponIssue issue2 = CouponIssue.builder().userId(2L).couponId(10L).build();
        ReflectionTestUtils.setField(issue2, "id", 101L);

        given(couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(10L))
                .willReturn(List.of(issue2, issue1));

        User user1 = User.builder().email("hong@example.com").name("홍길동").phone("010-1234-5678").build();
        ReflectionTestUtils.setField(user1, "id", 1L);
        User user2 = User.builder().email("kim@example.com").name("김철수").phone("010-9999-8888").build();
        ReflectionTestUtils.setField(user2, "id", 2L);
        given(userRepository.findAllById(anyList())).willReturn(List.of(user1, user2));

        List<CouponIssueHistoryResponse> result = couponIssueService.getIssuesByCouponId(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).issueId()).isEqualTo(101L);
        assertThat(result.get(0).userId()).isEqualTo(2L);
        assertThat(result.get(0).status()).isEqualTo(CouponIssueStatus.ISSUED);
        assertThat(result.get(0).userName()).isEqualTo("김**");
        assertThat(result.get(0).userEmail()).isEqualTo("k***@example.com");
        assertThat(result.get(0).userEmail()).doesNotContain("kim@");
        assertThat(result.get(1).userId()).isEqualTo(1L);
        assertThat(result.get(1).userName()).isEqualTo("홍**");
        assertThat(result.get(1).userEmail()).isEqualTo("h***@example.com");
        assertThat(result.get(1).userEmail()).doesNotContain("hong@");
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
        verify(userRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("시나리오7: 이력이 없으면 빈 리스트")
    void getIssuesByCouponId_empty() {
        given(couponRepository.existsById(10L)).willReturn(true);
        given(couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(10L)).willReturn(List.of());

        assertThat(couponIssueService.getIssuesByCouponId(10L)).isEmpty();
        verify(userRepository, never()).findAllById(anyList());
    }
}
