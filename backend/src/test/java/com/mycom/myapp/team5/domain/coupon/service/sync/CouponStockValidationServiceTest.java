package com.mycom.myapp.team5.domain.coupon.service.sync;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
public class CouponStockValidationServiceTest {

    private static final int REQUEST_COUNT = 100;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponIssueStreamProducer couponIssueStreamProducer;

    @Autowired
    private CouponStockValidationService couponStockValidationService;

    @Autowired
    private CouponStockRedisService couponStockRedisService;

    @MockitoSpyBean
    private MismatchNotifier mismatchNotifier;

    private final List<Long> createdCouponIds = new ArrayList<>();
    private List<Long> userIds;

    @AfterEach
    void tearDown() {
        createdCouponIds.forEach(couponIssueRepository::deleteByCouponId);
        createdCouponIds.forEach(couponRepository::deleteById);
        createdCouponIds.clear();
        if (userIds != null) {
            userRepository.deleteAllById(userIds);
        }
    }

    @Test
    public void 정상적으로_동기화된_쿠폰은_불일치로_잡히지_않는다() throws InterruptedException {
        long couponId = issueAndCloseCoupon();

        // 실제 이력과 정확히 일치하도록 S012가 채운 값 그대로 둔 상태에서 검증 배치를 돌린다.
        couponStockValidationService.verifyClosedCoupons();

        verify(mismatchNotifier, never()).notify(argThat(r -> r.couponId() == couponId));
    }

    @Test
    public void 기록된_값과_실제_이력이_다르면_불일치로_감지된다() throws InterruptedException {
        long couponId = issueAndCloseCoupon();

        // 드리프트를 인위로 재현: S012가 맞게 채워둔 값을 일부러 틀린 값으로 덮어쓴다.
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();
        coupon.syncIssuedQuantity(REQUEST_COUNT - 1);
        couponRepository.save(coupon);

        couponStockValidationService.verifyClosedCoupons();

        verify(mismatchNotifier, times(1)).notify(argThat(r ->
                r.couponId() == couponId
                        && r.recordedIssuedQuantity() == REQUEST_COUNT - 1
                        && r.actualIssuedCount() == REQUEST_COUNT
        ));
    }

    private long issueAndCloseCoupon() throws InterruptedException {
        userIds = new ArrayList<>(REQUEST_COUNT);
        for (int i = 0; i < REQUEST_COUNT; i++) {
            User user = userRepository.save(User.builder().email("validation-test-" + UUID.randomUUID() + "@test.com").build());
            userIds.add(user.getId());
        }

        Coupon coupon = Coupon.builder()
                .name("검증-테스트-쿠폰")
                .totalQuantity(REQUEST_COUNT)
                .startAt(LocalDateTime.now().minusMinutes(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
        coupon.open();
        coupon = couponRepository.save(coupon);
        long couponId = coupon.getId();
        createdCouponIds.add(couponId);
        // couponIssueStreamProducer.requestIssue()가 발급 전 Redis 재고를 확인하므로
        // 실제 서비스(CouponStatusServiceImpl.openCoupon())가 하는 initStock()을 여기서도 해줘야 한다.
        couponStockRedisService.initStock(couponId, REQUEST_COUNT);

        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        for (long userId : userIds) {
            executorService.execute(() -> couponIssueStreamProducer.requestIssue(couponId, userId));
        }
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() ->
                        assertThat(couponIssueRepository.countByCouponId(couponId)).isEqualTo((long) REQUEST_COUNT)
                );

        Coupon closing = couponRepository.findById(couponId).orElseThrow();
        closing.close();
        couponRepository.save(closing);

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Coupon result = couponRepository.findById(couponId).orElseThrow();
                    assertThat(result.getIssuedQuantity()).isEqualTo(REQUEST_COUNT);
                });

        return couponId;
    }

}
