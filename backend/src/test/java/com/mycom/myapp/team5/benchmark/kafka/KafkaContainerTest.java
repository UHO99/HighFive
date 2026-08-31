package com.mycom.myapp.team5.benchmark.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.kafka.enabled=true")
public class KafkaContainerTest {

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private MessageConsumer messageConsumer;

    private static final String TOPIC = "test-topic";

    @BeforeEach
    public void setup() {
        messageConsumer.getReceivedMessages().clear();
    }

    @Test
    public void testSendMessage() {
        String key = UUID.randomUUID().toString();
        String msg = "hello kafka" + key;

        messageProducer.sendMessage(TOPIC, key, msg);

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertThat(messageConsumer.getReceivedMessages()).contains(msg);
                });
    }

}
