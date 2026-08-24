package com.mycom.myapp.team5.domain.coupon.service;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * CouponOpenedEvent가 syncSubscriptions()의 2초 폴링을 기다리지 않고 즉시 구독을 트리거하는지 검증한다.
 * 폴링 주기(2초)보다 훨씬 짧은 시간 안에 발급 이력이 저장되면, 이벤트가 실제로 동작한 것이다.
 */
@SpringBootTest
public class CouponOpenedEventSubscriptionTest {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponIssueStreamProducer couponIssueStreamProducer;

    @Autowired
    private CouponStatusService couponStatusService;

    private Long couponId;
    private Long userId;

    @AfterEach
    void tearDown() {
        if (couponId != null) {
            couponIssueRepository.deleteByCouponId(couponId);
            couponRepository.deleteById(couponId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    public void openCoupon이_2초_폴링보다_먼저_구독을_등록한다() {
        Coupon coupon = Coupon.builder()
                .name("이벤트-구독-테스트-쿠폰")
                .totalQuantity(10)
                .startAt(LocalDateTime.now().minusMinutes(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
        coupon = couponRepository.save(coupon);
        couponId = coupon.getId();

        User user = userRepository.save(User.builder().email("event-test-" + UUID.randomUUID() + "@test.com").build());
        userId = user.getId();

        // CouponOpenedEvent가 AFTER_COMMIT으로 발행되는 지점 - 이 호출이 끝나는 순간 이미 구독이 걸려있어야 한다.
        couponStatusService.openCoupon(couponId);

        couponIssueStreamProducer.requestIssue(couponId, userId);

        // syncSubscriptions()의 폴링 주기(2초)보다 훨씬 짧게 잡는다 - 이 시간 안에 처리되면
        // 폴링이 아니라 이벤트가 구독을 걸었다는 뜻이다.
        await()
                .atMost(Duration.ofMillis(800))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() ->
                        assertThat(couponIssueRepository.countByCouponId(couponId)).isEqualTo(1L)
                );
    }

}
