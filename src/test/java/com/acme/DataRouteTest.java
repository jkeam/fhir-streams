package com.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class DataRouteTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    public void setUp() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("fhir-data"));
    }

    @AfterEach
    public void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    public void testDataEndpointReturnsSuccess() {
        String requestBody = "{\"patient\":\"12345\",\"status\":\"active\",\"timestamp\":\"2026-07-02T10:00:00Z\"}";

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/data")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", is("success"))
            .body("message", is("Data sent to Kafka"));
    }

    @Test
    public void testMessageSentToKafka() {
        String requestBody = "{\"patient\":\"67890\",\"status\":\"pending\",\"timestamp\":\"2026-07-03T12:00:00Z\"}";

        // Send the request
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/data")
            .then()
            .statusCode(200);

        // Wait for and verify message was sent to Kafka
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            assertFalse(records.isEmpty(), "Should receive at least one message");

            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().contains("\"patient\":\"67890\"") &&
                    record.value().contains("\"status\":\"pending\"")) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Should contain the test message");
        });
    }

    @Test
    public void testMultipleMessages() {
        // Send multiple messages
        for (int i = 0; i < 3; i++) {
            String requestBody = String.format(
                "{\"patient\":\"patient-%d\",\"status\":\"active\",\"timestamp\":\"2026-07-03T12:00:00Z\"}",
                i
            );

            given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/data")
                .then()
                .statusCode(200);
        }

        // Wait for and verify all messages were sent to Kafka
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> messages = new ArrayList<>();
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, String> record : records) {
                messages.add(record.value());
            }

            assertTrue(messages.size() >= 3, "Should receive at least 3 messages");

            for (int i = 0; i < 3; i++) {
                final int index = i;
                assertTrue(
                    messages.stream().anyMatch(m -> m.contains("\"patient\":\"patient-" + index + "\"")),
                    "Should contain message for patient-" + index
                );
            }
        });
    }

    @Test
    public void testInvalidJsonReturns400() {
        String invalidJson = "{invalid json}";

        given()
            .contentType(ContentType.JSON)
            .body(invalidJson)
            .when()
            .post("/api/data")
            .then()
            .statusCode(400);
    }
}
