package com.acme;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class DataRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // REST endpoint that accepts POST requests
        rest("/api")
            .post("/data")
            .consumes("application/json")
            .produces("application/json")
            .to("direct:sendToKafka");

        // Route to send data to Kafka topic
        from("direct:sendToKafka")
            .log("Received data: ${body}")
            .onException(Exception.class)
                .log("ERROR: Failed to send message to Kafka: ${exception.message}")
                .handled(true)
                .setBody(constant("{\"status\":\"error\",\"message\":\"Failed to send to Kafka\"}"))
            .end()
            .to("kafka:fhir-data?brokers={{kafka.bootstrap.servers}}")
            .log("SUCCESS: Message sent to Kafka topic fhir-data")
            .setBody(constant("{\"status\":\"success\",\"message\":\"Data sent to Kafka\"}"));
    }
}
