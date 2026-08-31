package com.mycom.myapp.team5.benchmark.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class CouponKafkaTopicConfig {

    @Bean
    public NewTopic couponIssueRequestTopic() {
        return TopicBuilder.name(CouponRequestProducer.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

}
