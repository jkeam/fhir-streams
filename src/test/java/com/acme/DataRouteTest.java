package com.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
public class DataRouteTest {

    @InjectKafkaCompanion
    KafkaCompanion companion;


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
        String uniqueId = "test-msg-" + System.currentTimeMillis();
        String requestBody = String.format(
            "{\"patient\":\"%s\",\"status\":\"pending\",\"timestamp\":\"2026-07-03T12:00:00Z\"}",
            uniqueId
        );

        // Send the request
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/data")
            .then()
            .statusCode(200);

        // Wait for and verify message was sent to Kafka
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            List<String> messages = companion.consumeStrings()
                .fromTopics("fhir-data", 100, Duration.ofSeconds(2))
                .awaitCompletion()
                .getRecords()
                .stream()
                .map(record -> record.value())
                .collect(Collectors.toList());

            boolean found = messages.stream().anyMatch(msg ->
                msg.contains("\"patient\":\"" + uniqueId + "\"")
            );
            assertTrue(found, "Should contain the test message with ID: " + uniqueId + ". Found " + messages.size() + " messages.");
        });
    }

    @Test
    public void testMultipleMessages() {
        String testId = "multi-test-" + System.currentTimeMillis();

        // Send multiple messages
        for (int i = 0; i < 3; i++) {
            String requestBody = String.format(
                "{\"patient\":\"%s-%d\",\"status\":\"active\",\"timestamp\":\"2026-07-03T12:00:00Z\"}",
                testId, i
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
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            List<String> messages = companion.consumeStrings()
                .fromTopics("fhir-data", 100, Duration.ofSeconds(2))
                .awaitCompletion()
                .getRecords()
                .stream()
                .map(record -> record.value())
                .collect(Collectors.toList());

            for (int i = 0; i < 3; i++) {
                final int index = i;
                assertTrue(
                    messages.stream().anyMatch(m -> m.contains("\"patient\":\"" + testId + "-" + index + "\"")),
                    "Should contain message for " + testId + "-" + index + ". Found " + messages.size() + " messages."
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
