package com.acme;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class KafkaHealthCheck implements HealthCheck {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String kafkaBootstrapServers;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("kafka-connection")
                .withData("brokers", kafkaBootstrapServers)
                .up()
                .build();
    }
}
