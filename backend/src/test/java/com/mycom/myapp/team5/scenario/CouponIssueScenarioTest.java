package com.mycom.myapp.team5.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamConsumer;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStreamKeys;

/**
 * "유저가 HTTP로 발급 요청을 보냈을 때 실제로 발급이 되는가"를 끝까지 확인하는 시나리오 테스트.
 *
 * CouponControllerTest는 HTTP 응답(202/409/204)까지만 검증하고, coupon_issue 저장은 확인하지 않는다.
 * 저장은 Redis Stream Consumer(CouponIssueStreamConsumer, S007)가 비동기 배치로 처리하므로,
 * HTTP 응답이 202라고 해서 그 순간 DB에 반영됐다는 보장이 없다 - 그래서 Awaitility로
 * "결국에는 반영된다"를 기다려 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class CouponIssueScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponStatusService couponStatusService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CouponIssueStreamConsumer couponIssueStreamConsumer;

    private final List<Long> createdCouponIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdCouponIds.forEach(couponIssueRepository::deleteByCouponId);
        createdCouponIds.forEach(couponRepository::deleteById);

        createdCouponIds.forEach(couponId -> {
            String streamKey = CouponStreamKeys.streamKey(couponId);
            await()
                    .atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> !couponIssueStreamConsumer.isSubscribed(streamKey));

            stringRedisTemplate.delete(CouponStockKeys.stockKey(couponId));
        });
        createdCouponIds.clear();

        userRepository.deleteAllById(createdUserIds);
        createdUserIds.clear();
    }

    @Test
    void HTTP로_발급_요청하면_결국_coupon_issue_테이블에_저장된다() throws Exception {
        long userId = createUser();
        long couponId = createOpenCoupon(10);

        mockMvc.perform(post("/coupons/{couponId}/issue", couponId).param("userId", String.valueOf(userId))).andExpect(status().isAccepted());

        await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() ->
                        assertThat(couponIssueRepository.countByCouponId(couponId)).isEqualTo(1L)
                );

        List<CouponIssue> issued = couponIssueRepository.findAll().stream()
                .filter(ci -> ci.getCouponId().equals(couponId))
                .toList();
        assertThat(issued).hasSize(1);
        assertThat(issued.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void 재고보다_많은_인원이_동시에_HTTP로_요청해도_재고만큼만_DB에_저장된다() throws Exception {
        int stock = 10000;
        int requestCount = 20000;

        List<Long> userIds = createUsers(requestCount);
        long couponId = createOpenCoupon(stock);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        for (long userId : userIds) {
            executor.execute(() -> {
                try {
                    mockMvc.perform(post("/coupons/{couponId}/issue", couponId).param("userId", String.valueOf(userId)));
                } catch (Exception e) {
                    // 품절(204)/중복(409)도 정상 응답이므로 여기서는 무시하고, 최종 DB 건수로만 판정한다.
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() ->
                        assertThat(couponIssueRepository.countByCouponId(couponId)).isEqualTo((long) stock)
                );

        // 그 뒤로도 더 안 늘어나는지(초과 발급 없는지) 한 번 더 확인
        Thread.sleep(1000);
        assertThat(couponIssueRepository.countByCouponId(couponId)).isEqualTo((long) stock);
    }

    private long createUser() {
        User user = userRepository.save(User.builder()
                .email("scenario-" + UUID.randomUUID() + "@test.com")
                .build());
        createdUserIds.add(user.getId());
        return user.getId();
    }

    private List<Long> createUsers(int count) {
        List<User> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            users.add(User.builder().email("scenario-" + UUID.randomUUID() + "@test.com").build());
        }
        List<Long> userIds = userRepository.saveAll(users).stream().map(User::getId).collect(Collectors.toList());
        createdUserIds.addAll(userIds);
        return userIds;
    }

    private long createOpenCoupon(int totalQuantity) {
        Coupon coupon = Coupon.builder()
                .name("시나리오-테스트-쿠폰")
                .totalQuantity(totalQuantity)
                .startAt(LocalDateTime.now().minusMinutes(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
        Coupon saved = couponRepository.save(coupon);
        long couponId = saved.getId();
        createdCouponIds.add(couponId);

        // openCoupon()이 Redis 재고 초기화 + CouponOpenedEvent 발행(Stream 구독 즉시 트리거)까지 해준다.
        couponStatusService.openCoupon(couponId);
        return couponId;
    }
}
