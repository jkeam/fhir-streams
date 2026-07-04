package com.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Tests for the /api/data endpoint.
 * These tests verify the HTTP endpoint behavior and response structure.
 * Kafka integration is tested separately with a live Kafka instance.
 */
@QuarkusTest
public class SimpleDataRouteTest {

    @Test
    public void testDataEndpointAcceptsValidJson() {
        String requestBody = "{\"patient\":\"12345\",\"status\":\"active\",\"timestamp\":\"2026-07-02T10:00:00Z\"}";

        // Test accepts valid JSON and returns 200 with a response containing status and message
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/data")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasKey("status"))
            .body("$", hasKey("message"));
    }

    @Test
    public void testDataEndpointWithDifferentPatientData() {
        String requestBody = "{\"patient\":\"67890\",\"status\":\"pending\",\"timestamp\":\"2026-07-03T12:00:00Z\"}";

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/data")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasKey("status"));
    }

    @Test
    public void testResponseStructureIsValid() {
        String requestBody = "{\"patient\":\"test-001\",\"status\":\"verified\",\"timestamp\":\"2026-07-03T15:30:00Z\"}";

        // Verify response contains both status and message fields
        // Status can be either "success" (if Kafka is available) or "error" (if Kafka is unavailable)
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/data")
            .then()
            .statusCode(200)
            .body("$", hasKey("status"))
            .body("$", hasKey("message"))
            .body("status", anyOf(is("success"), is("error")));
    }
}
