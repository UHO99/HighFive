package com.mycom.myapp.team5.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

/**
 * 쿠폰 발급 스트림 전용 리스너 컨테이너. Spring이 관리하는 Lifecycle 빈이라 컨텍스트 시작/종료에
 * 맞춰 자동으로 시작/정지된다 - @Scheduled 폴링과 달리 다른 배치(S012/S013)와 스레드를 공유하지 않고,
 * 컨테이너 자체 스레드에서 각 구독(Subscription)의 블로킹 read를 돌린다.
 */
@Slf4j
@Configuration
public class RedisStreamContainerConfig {

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> couponIssueStreamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .batchSize(500)
                        .errorHandler(throwable ->
                                log.error("Redis Stream 구독 처리 중 예외 발생 - 해당 구독은 계속 유지됨", throwable))
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

}
