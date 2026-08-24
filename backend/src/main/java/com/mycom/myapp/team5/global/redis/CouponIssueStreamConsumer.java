package com.mycom.myapp.team5.global.redis;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.event.CouponOpenedEvent;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.monitoring.metric.DbInsertMetricsRecorder;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.config.RedisStreamConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 쿠폰별로 나뉜 Redis Stream(coupon:issue:request:stream:{couponId})을 읽어 coupon_issue에 배치 insert한다.
 *
 * @Scheduled 폴링 대신 StreamMessageListenerContainer의 구독(Subscription)을 쓴다 - 각 스트림마다
 * 컨테이너 전용 스레드가 블로킹 read를 계속 돌리므로, S012/S013과 스레드를 공유하지 않고 폴링 텀에 따른
 * 지연도 없다. 다만 리스너 콜백(onMessage)은 레코드 1건씩 들어오므로, JDBC batch insert 이점을 유지하려면
 * 버퍼에 모았다가 별도 스레드(flush)가 묶어서 처리하는 구조가 필요하다.
 *
 * "어떤 스트림을 구독할지"는 두 경로로 갱신된다.
 * ① CouponOpenedEvent(AFTER_COMMIT) - OPEN 트랜잭션이 커밋되는 즉시 구독한다. 트래픽이 제일
 *    몰리는 순간이 하필 OPEN 시점이라, 폴링 텀만큼의 지연도 그 순간에는 부담이라 이벤트로 없앴다.
 * ② syncSubscriptions() - 2초 주기 폴링. ①이 누락되는 경우(재시작 등)를 대비한 안전망일 뿐,
 *    주 발견 수단이 아니다. DB 조회 + 구독 등록/해제만 하는 저비용 작업이라 지연돼도 이미 구독 중인
 *    스트림의 메시지 처리에는 영향이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final int BATCH_SIZE = 500;
    private static final String INSERT_SQL =
    """
    INSERT INTO coupon_issue (user_id, coupon_id, status, issued_at)
    VALUES (?, ?, 'ISSUED', NOW())
    ON DUPLICATE KEY UPDATE id = id
    """;

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer;
    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final CouponRepository couponRepository;
    private final RedisStreamConfig redisStreamConfig;
    private final DbInsertMetricsRecorder dbInsertMetricsRecorder;

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final BlockingQueue<MapRecord<String, String, String>> buffer = new LinkedBlockingQueue<>();

    private volatile boolean running = false;
    private Thread flushThread;

    // 테스트가 "구독 해제까지 끝났는지"를 폴링으로 확인할 수 있게 열어둔 조회용 메서드.
    // 구독 해제 전에 스트림 키를 지우면 이 스트림을 폴링 중이던 컨슈머가 NOGROUP 예외를 겪는다.
    public boolean isSubscribed(String streamKey) {
        return subscriptions.containsKey(streamKey);
    }

    // 모니터링 대시보드용 - 현재 이 컨슈머가 붙들고 있는 스트림(=쿠폰) 구독 수.
    public int activeSubscriptionCount() {
        return subscriptions.size();
    }

    // 컨테이너 전용 스레드에서 레코드 1건씩 호출된다 - 여기서 무거운 작업(JDBC)을 하면 그 스트림의
    // 다음 read가 막히므로, 버퍼에 넣고 바로 리턴한다. 실제 insert는 flushLoop()가 따로 한다.
    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        buffer.add(message);
    }

    // OPEN 트랜잭션 커밋 직후 즉시 호출된다 - 2초 폴링을 기다리지 않는다.
    // AFTER_COMMIT이라 트랜잭션이 롤백되면 아예 호출되지 않는다(롤백된 OPEN을 구독하는 일이 없다).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCouponOpened(CouponOpenedEvent event) {
        subscribeIfAbsent(CouponStreamKeys.streamKey(event.couponId()));
    }

    // 안전망(이벤트)이 누락됐을 때(앱 재시작 등)만 실질적으로 의미가 있다. 새로 생긴/빠져야 할
    // 스트림만 가볍게 반영한다. 2초 지연은 "새 쿠폰을 알아채는 지연"일 뿐, 이미 구독 중인 스트림의
    // 메시지 처리 지연이 아니다.
    @Scheduled(fixedDelay = 2000)
    public void syncSubscriptions() {
        Set<String> desired = activeStreamKeys();

        desired.forEach(this::subscribeIfAbsent);

        subscriptions.keySet().stream()
                .filter(key -> !desired.contains(key))
                .forEach(this::unsubscribe);
    }

    private Set<String> activeStreamKeys() {
        List<Coupon> open = couponRepository.findByStatus(CouponStatus.OPEN);
        List<Coupon> draining = couponRepository.findByStatusAndIssuedQuantityIsNull(CouponStatus.CLOSE);

        Set<String> streamKeys = new HashSet<>(open.size() + draining.size());
        open.forEach(coupon -> streamKeys.add(CouponStreamKeys.streamKey(coupon.getId())));
        draining.forEach(coupon -> streamKeys.add(CouponStreamKeys.streamKey(coupon.getId())));
        return streamKeys;
    }

    private void subscribeIfAbsent(String streamKey) {
        subscriptions.computeIfAbsent(streamKey, key -> {
            redisStreamConfig.ensureConsumerGroup(key);
            Subscription subscription = streamContainer.receive(
                    Consumer.from(CouponStreamKeys.CONSUMER_GROUP, CouponStreamKeys.CONSUMER_NAME),
                    StreamOffset.create(key, ReadOffset.lastConsumed()),
                    this
            );
            log.info("쿠폰 발급 스트림 구독 시작 - streamKey={}", key);
            return subscription;
        });
    }

    private void unsubscribe(String streamKey) {
        Subscription subscription = subscriptions.remove(streamKey);
        if (subscription == null) {
            return;
        }
        try {
            subscription.cancel();
            log.info("쿠폰 발급 스트림 구독 해제 - streamKey={}", streamKey);
        } catch (DataAccessException e) {
            log.debug("스트림 구독 해제 중 예외(무시 가능) - streamKey={}", streamKey, e);
        }
    }

    @PostConstruct
    public void startFlushLoop() {
        running = true;
        flushThread = new Thread(this::flushLoop, "coupon-issue-flush");
        flushThread.setDaemon(true);
        flushThread.start();
    }

    @PreDestroy
    public void stopFlushLoop() {
        running = false;
        if (flushThread != null) {
            flushThread.interrupt();
            try {
                flushThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        subscriptions.keySet().forEach(this::unsubscribe);
    }

    private void flushLoop() {
        while (running) {
            try {
                List<MapRecord<String, String, String>> batch = drainBatch();
                if (batch.isEmpty()) {
                    continue;
                }
                try {
                    insertBatch(batch);
                    dbInsertMetricsRecorder.recordBatch(batch.size());
                    log.info("발급 이력 배치 저장 완료 - count={}", batch.size());
                    acknowledge(batch);
                } catch (DataAccessException e) {
                    log.error("배치 insert 실패(제약 위반 가능성) - 건별로 재시도합니다. count={}", batch.size(), e);
                    insertIndividually(batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // @Scheduled가 예외를 삼키고 다음 사이클을 이어가는 것과 동일한 안전장치 -
                // 여기서 못 잡으면 flush 스레드 자체가 죽어서 그 순간부터 저장이 영구히 멈춘다.
                log.error("발급 이력 배치 처리 중 예상치 못한 예외 - 다음 사이클로 계속", e);
            }
        }
    }

    private List<MapRecord<String, String, String>> drainBatch() throws InterruptedException {
        List<MapRecord<String, String, String>> batch = new ArrayList<>(BATCH_SIZE);
        MapRecord<String, String, String> first = buffer.poll(200, TimeUnit.MILLISECONDS);
        if (first == null) {
            return batch;
        }
        batch.add(first);
        buffer.drainTo(batch, BATCH_SIZE - 1);
        return batch;
    }

    private void insertBatch(List<MapRecord<String, String, String>> records) {
        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map<String, String> fields = records.get(i).getValue();
                ps.setLong(1, Long.parseLong(fields.get("userId")));
                ps.setLong(2, Long.parseLong(fields.get("couponId")));
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    // 배치 전체가 실패했을 때만 타는 경로 - 문제 있는 건만 정확히 걸러내기 위해 한 건씩 재시도한다.
    // 실패한 건은 ACK하지 않고 남겨서(PEL) 조용히 유실되지 않게 한다.
    private void insertIndividually(List<MapRecord<String, String, String>> records) {
        for (MapRecord<String, String, String> record : records) {
            Map<String, String> fields = record.getValue();
            long userId = Long.parseLong(fields.get("userId"));
            long couponId = Long.parseLong(fields.get("couponId"));

            try {
                jdbcTemplate.update(INSERT_SQL, userId, couponId);
                dbInsertMetricsRecorder.recordBatch(1);
                acknowledge(record.getStream(), record.getId());
            } catch (DataAccessException e) {
                log.error("발급 이력 저장 실패(제약 위반) - couponId={}, userId={}, recordId={} - ACK 보류, 확인 필요", couponId, userId, record.getId(), e);
            }
        }
    }

    private void acknowledge(List<MapRecord<String, String, String>> records) {
        Map<String, List<RecordId>> byStream = records.stream()
                .collect(Collectors.groupingBy(
                        MapRecord::getStream,
                        Collectors.mapping(Record::getId, Collectors.toList())
                ));
        byStream.forEach((streamKey, recordIds) -> acknowledge(streamKey, recordIds.toArray(new RecordId[0])));
    }

    private void acknowledge(String streamKey, RecordId... recordIds) {
        stringRedisTemplate.opsForStream().acknowledge(streamKey, CouponStreamKeys.CONSUMER_GROUP, recordIds);
    }
}
