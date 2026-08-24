package com.mycom.myapp.team5.domain.coupon.service;

import com.mycom.myapp.team5.domain.coupon.dto.CouponOverviewResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponRequest;
import com.mycom.myapp.team5.domain.coupon.dto.CouponResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponUpdateRequest;
import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponAdminUserApiTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponIssueRepository couponIssueRepository;
    @Mock
    private CouponStockRedisService couponStockRedisService;

    @InjectMocks
    private CouponServiceImpl couponService;

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 25, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 25, 18, 0);

    private Coupon readyCoupon(long id, int quantity) {
        Coupon coupon = Coupon.builder()
                .name("테스트쿠폰")
                .totalQuantity(quantity)
                .startAt(START)
                .endAt(END)
                .build();
        ReflectionTestUtils.setField(coupon, "id", id);
        return coupon;
    }

    @Nested
    @DisplayName("A003 쿠폰 생성")
    class A003Create {

        @Test
        @DisplayName("정상 생성 시 READY로 저장된다")
        void create_success() {
            CouponRequest request = new CouponRequest("선착쿠폰", 1000, START, END);
            given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> {
                Coupon c = inv.getArgument(0);
                ReflectionTestUtils.setField(c, "id", 1L);
                return c;
            });

            CouponResponse response = couponService.create(request);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("선착쿠폰");
            assertThat(response.totalQuantity()).isEqualTo(1000);
            assertThat(response.status()).isEqualTo(CouponStatus.READY);
            verify(couponStockRedisService, never()).initStock(any(Long.class), any(Integer.class));
        }

        @Test
        @DisplayName("기간이 잘못되면 CP004")
        void create_invalidPeriod() {
            CouponRequest request = new CouponRequest("선착쿠폰", 100, END, START);

            assertThatThrownBy(() -> couponService.create(request))
                    .isInstanceOf(CouponException.class)
                    .extracting(ex -> ((CouponException) ex).getErrorCode())
                    .isEqualTo(CouponErrorCode.COUPON_INVALID_PERIOD);
        }
    }

    @Nested
    @DisplayName("A004 재고/기간 수정")
    class A004Update {

        @Test
        @DisplayName("READY 쿠폰 재고·기간을 수정한다")
        void update_ready_success() {
            Coupon coupon = readyCoupon(1L, 100);
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            CouponUpdateRequest request = new CouponUpdateRequest(
                    500,
                    START.plusHours(1),
                    END.plusHours(1)
            );

            CouponResponse response = couponService.update(1L, request);

            assertThat(response.totalQuantity()).isEqualTo(500);
            assertThat(response.startAt()).isEqualTo(START.plusHours(1));
            assertThat(response.endAt()).isEqualTo(END.plusHours(1));
        }

        @Test
        @DisplayName("OPEN 쿠폰 수정 시 CP003")
        void update_open_conflict() {
            Coupon coupon = readyCoupon(1L, 100);
            coupon.open();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            assertThatThrownBy(() -> couponService.update(1L, new CouponUpdateRequest(200, null, null)))
                    .isInstanceOf(CouponException.class)
                    .extracting(ex -> ((CouponException) ex).getErrorCode())
                    .isEqualTo(CouponErrorCode.COUPON_STATUS_CONFLICT);
        }

        @Test
        @DisplayName("없는 쿠폰이면 CP001")
        void update_notFound() {
            given(couponRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.update(99L, new CouponUpdateRequest(10, null, null)))
                    .isInstanceOf(CouponException.class)
                    .extracting(ex -> ((CouponException) ex).getErrorCode())
                    .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("U001 목록/상세")
    class U001ListDetail {

        @Test
        @DisplayName("목록을 반환한다")
        void list() {
            given(couponRepository.findAll()).willReturn(List.of(readyCoupon(1L, 10), readyCoupon(2L, 20)));

            List<CouponResponse> list = couponService.getCoupons();

            assertThat(list).hasSize(2);
            assertThat(list.get(0).id()).isEqualTo(1L);
            assertThat(list.get(1).totalQuantity()).isEqualTo(20);
        }

        @Test
        @DisplayName("상세 조회")
        void detail() {
            given(couponRepository.findById(1L)).willReturn(Optional.of(readyCoupon(1L, 10)));

            CouponResponse response = couponService.getCoupon(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("테스트쿠폰");
        }

        @Test
        @DisplayName("상세 미존재 CP001")
        void detail_notFound() {
            given(couponRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.getCoupon(1L))
                    .isInstanceOf(CouponException.class)
                    .extracting(ex -> ((CouponException) ex).getErrorCode())
                    .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("A005 현황 조회")
    class A005Overview {

        @Test
        @DisplayName("발급 건수·DB 잔여·Redis 잔여를 반환한다")
        void overview() {
            Coupon coupon = readyCoupon(1L, 100);
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));
            given(couponIssueRepository.countByCouponId(1L)).willReturn(30L);
            given(couponStockRedisService.getStock(1L)).willReturn(70);

            CouponOverviewResponse overview = couponService.getOverview(1L);

            assertThat(overview.totalQuantity()).isEqualTo(100);
            assertThat(overview.issuedQuantity()).isEqualTo(30L);
            assertThat(overview.remainingQuantity()).isEqualTo(70L);
            assertThat(overview.redisRemainingQuantity()).isEqualTo(70);
            assertThat(overview.status()).isEqualTo(CouponStatus.READY);
        }

        @Test
        @DisplayName("issuedQuantity가 있으면 DB 카운트 대신 사용한다")
        void overview_usesSyncedIssuedQuantity() {
            Coupon coupon = readyCoupon(1L, 100);
            coupon.syncIssuedQuantity(40);
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));
            given(couponStockRedisService.getStock(1L)).willReturn(null);

            CouponOverviewResponse overview = couponService.getOverview(1L);

            assertThat(overview.issuedQuantity()).isEqualTo(40L);
            assertThat(overview.remainingQuantity()).isEqualTo(60L);
            assertThat(overview.redisRemainingQuantity()).isNull();
            verify(couponIssueRepository, never()).countByCouponId(any());
        }

        @Test
        @DisplayName("전체 현황 목록")
        void overviews() {
            given(couponRepository.findAll()).willReturn(List.of(readyCoupon(1L, 50)));
            given(couponIssueRepository.countByCouponId(1L)).willReturn(5L);
            given(couponStockRedisService.getStock(1L)).willReturn(45);

            List<CouponOverviewResponse> list = couponService.getOverviews();

            assertThat(list).hasSize(1);
            assertThat(list.get(0).issuedQuantity()).isEqualTo(5L);
        }
    }
}
