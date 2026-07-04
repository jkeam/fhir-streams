# Running the Application

This guide shows how to run the FHIR Streams application in different environments.

## Prerequisites

### For DEV Mode (Local)
- Java 17+
- Maven 3.8+
- Local Kafka running: `docker compose up -d`

### For PROD Mode (OpenShift)
- Java 17+
- Maven 3.8+
- Access to OpenShift Kafka cluster
- Truststore configured (see KAFKA-SETUP.md)

## Development Mode (Local Kafka)

**Start local Kafka first:**
```bash
docker compose up -d
```

**Run the application:**
```bash
mvn quarkus:dev
```

This will:
- Connect to `localhost:9092`
- Use PLAINTEXT (no SSL)
- Enable hot reload (changes auto-reload)
- Start on http://localhost:8080

**Test it:**
```bash
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{"patient":"12345","status":"active","timestamp":"2026-07-03T10:00:00Z"}'
```

**View messages in Kafka:**
```bash
podman exec -it kafka-local /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic fhir-data \
  --from-beginning
```

**Stop (Ctrl+C in terminal, then):**
```bash
docker compose down
```

---

## Production Mode (OpenShift Kafka)

**Build the application:**
```bash
mvn clean package
```

**Run in production mode:**
```bash
java -jar target/quarkus-app/quarkus-run.jar
```

This will:
- Connect to OpenShift Kafka cluster
- Use SSL/TLS with truststore
- Start on http://localhost:8080

**Verify it's using PROD profile:**
Look for this in the logs:
```
kafka.bootstrap.servers=<your-server>
```

**Test it:**
```bash
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{"patient":"99999","status":"verified","timestamp":"2026-07-03T15:00:00Z"}'
```

**Check logs for success:**
```
SUCCESS: Message sent to Kafka topic fhir-data
```

---

## Running Tests

**Run all tests:**
```bash
mvn test
```

**Run specific test:**
```bash
mvn test -Dtest=SimpleDataRouteTest
```

Tests automatically use the `test` profile with mock Kafka configuration.

---

## Force a Specific Profile

### Force DEV profile
```bash
mvn quarkus:dev -Dquarkus.profile=dev
```

### Force PROD profile in dev mode
```bash
mvn quarkus:dev -Dquarkus.profile=prod
```

### Run JAR with specific profile
```bash
java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar
```

---

## Troubleshooting

### "Connection refused" to localhost:9092
**Problem:** Local Kafka is not running  
**Solution:**
```bash
docker compose up -d
podman ps | grep kafka  # verify it's running
```

### "SSL handshake failed" 
**Problem:** Running in prod mode but truststore is missing  
**Solution:** Follow KAFKA-SETUP.md to create truststore

### "Topic fhir-data not found"
**Problem:** Topic doesn't exist  
**Solution:**
```bash
# For local Kafka
podman exec -it kafka-local /opt/kafka/bin/kafka-topics.sh \
  --create --bootstrap-server localhost:9092 \
  --topic fhir-data --partitions 1 --replication-factor 1

# For OpenShift
oc apply -f openshift/kafka/fhir-data-topic.yaml -n fhir
```

### Check which profile is active
**Look for this log line when app starts:**
```
Profile dev activated. Live Coding activated.
```
or
```
Profile prod activated.
```

### View current configuration
Add this to application.properties to see all active config on startup:
```properties
quarkus.log.category."io.quarkus.config".level=DEBUG
```

---

## Quick Commands Cheat Sheet

```bash
# Development (local Kafka)
docker compose up -d              # Start Kafka
mvn quarkus:dev                   # Run app in dev mode
curl -X POST http://localhost:8080/api/data -H "Content-Type: application/json" -d '{"patient":"123","status":"active","timestamp":"2026-07-03T10:00:00Z"}'
docker compose down               # Stop Kafka

# Production (OpenShift Kafka)
mvn clean package                 # Build
java -jar target/quarkus-app/quarkus-run.jar  # Run

# Testing
mvn test                          # Run all tests
mvn test -Dtest=SimpleDataRouteTest  # Run specific test

# Verify Kafka
podman ps | grep kafka            # Check if running
podman logs -f kafka-local        # View logs
```

---

## Environment Variables

You can also override properties with environment variables:

```bash
# Override Kafka bootstrap servers
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
mvn quarkus:dev

# Override HTTP port
export QUARKUS_HTTP_PORT=9090
mvn quarkus:dev
```

Quarkus converts property names to env vars:
- `kafka.bootstrap.servers` → `KAFKA_BOOTSTRAP_SERVERS`
- `quarkus.http.port` → `QUARKUS_HTTP_PORT`
