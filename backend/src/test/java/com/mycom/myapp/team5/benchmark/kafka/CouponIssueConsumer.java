package com.mycom.myapp.team5.benchmark.kafka;

import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class CouponIssueConsumer {

    private final CouponService couponService;

    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger duplicateCount = new AtomicInteger(0);
    private final Set<Long> issuedUserIds = ConcurrentHashMap.newKeySet();

    @KafkaListener(
            topics = CouponRequestProducer.TOPIC,
            groupId = "coupon-issue-group",
            concurrency = "12",
            containerFactory = "couponBatchKafkaListenerContainerFactory"
    )
    public void consume(List<String> messages) {
        // 같은 poll에서 받아온 메시지를 couponId 별로 모아 쿠폰당 트랜잭션 1건으로 처리한다.
        Map<Long, List<Long>> userIdsByCoupon = new LinkedHashMap<>();
        for (String message : messages) {
            String[] parts = message.split(":");
            long couponId = Long.parseLong(parts[0]);
            long userId = Long.parseLong(parts[1]);
            userIdsByCoupon.computeIfAbsent(couponId, key -> new ArrayList<>()).add(userId);
        }

        for (Map.Entry<Long, List<Long>> entry : userIdsByCoupon.entrySet()) {
            long couponId = entry.getKey();
            List<Long> userIds = entry.getValue();

            int granted = couponService.decreaseStockBatch(couponId, userIds.size());

            for (int i = 0; i < userIds.size(); i++) {
                long userId = userIds.get(i);
                if (i < granted) {
                    successCount.incrementAndGet();
                    // Kafka는 at-least-once라 재처리 시 같은 유저가 두 번 성공할 수 있다 - 여기서 걸러낸다.
                    if (!issuedUserIds.add(userId)) {
                        duplicateCount.incrementAndGet();
                        log.warn("동일 유저에게 중복 발급 발생 - couponId={}, userId={}", couponId, userId);
                    }
                } else {
                    log.debug("쿠폰 재고 소진으로 발급 실패 - couponId={}, userId={}", couponId, userId);
                }
            }
        }

        processedCount.addAndGet(messages.size());
    }

    public void reset() {
        processedCount.set(0);
        successCount.set(0);
        duplicateCount.set(0);
        issuedUserIds.clear();
    }
}
