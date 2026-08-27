package com.mycom.myapp.team5.benchmark.kafka;

import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class MessageConsumer {

    private final List<String> receivedMessages = new ArrayList<>();

    @KafkaListener(topics = "test-topic", groupId = "test-group")
    public void setReceivedMessages(String message) {
        receivedMessages.add(message);
    }

}
