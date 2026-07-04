# FHIR Streams - Quarkus Kamelet Application

A simple Quarkus application that exposes a REST endpoint to receive data
and immediately sends it to a Kafka topic.

## Prerequisites

- Java 17+
- Maven 3.8+
- Kafka running locally (or update the broker configuration)

## Quick Start with Kafka

### Option 1: Using Docker Compose (Recommended)

Start Kafka using the included docker-compose.yml:

```bash
docker-compose up -d
```

Stop Kafka:

```bash
docker-compose down
```

View Kafka logs:

```bash
docker-compose logs -f kafka
```

### Option 2: Using Podman/Docker (Simple Command)

```bash
podman run -d --name kafka-local \
  -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  apache/kafka:latest
```

Stop and remove the container:

```bash
podman stop kafka-local
podman rm kafka-local
```

View logs:

```bash
podman logs -f kafka-local
```

### Option 3: Using Homebrew (macOS)

```bash
# Install Kafka
brew install kafka

# Start Kafka (includes ZooKeeper)
brew services start kafka

# Stop Kafka
brew services stop kafka
```

### Verify Kafka is Running

Check if Kafka is listening on port 9092:

```bash
# On macOS/Linux
nc -zv localhost 9092

# Or check the container status
podman ps | grep kafka
```

## Running the Application

The application supports multiple profiles for different environments:

### Development Mode (Local Kafka)

**Start local Kafka first:**
```bash
docker compose up -d
```

**Run the application:**
```bash
mvn quarkus:dev
```

This automatically uses the `dev` profile which connects to `localhost:9092` without SSL.

### Production Mode (OpenShift Kafka)

**Build and run:**
```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

This uses the `prod` profile which connects to the OpenShift Kafka cluster with SSL.

### Force a Specific Profile

```bash
# Force dev profile
mvn quarkus:dev -Dquarkus.profile=dev

# Force prod profile
mvn quarkus:dev -Dquarkus.profile=prod

# Run JAR with specific profile
java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar
```

**See [RUNNING.md](RUNNING.md) for complete details on running in different modes.**

## Testing the Endpoint

Send data to the REST endpoint:

```bash
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{"patient":"12345","status":"active","timestamp":"2026-07-02T10:00:00Z"}'
```

Expected response:

```json
{"status":"success","message":"Data sent to Kafka"}
```

## Consuming from Kafka

To verify messages are being sent to Kafka:

### Using Docker/Podman:

```bash
podman exec -it kafka-local /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic fhir-data \
  --from-beginning
```

### Create the Topic Manually (if needed):

```bash
podman exec -it kafka-local /opt/kafka/bin/kafka-topics.sh \
  --create \
  --bootstrap-server localhost:9092 \
  --topic fhir-data \
  --partitions 1 \
  --replication-factor 1
```

### List Topics:

```bash
podman exec -it kafka-local /opt/kafka/bin/kafka-topics.sh \
  --list \
  --bootstrap-server localhost:9092
```

## Configuration

Edit `src/main/resources/application.properties` to customize:

- `kafka.bootstrap.servers`: Kafka broker address (default: localhost:9092)
- `quarkus.http.port`: HTTP port (default: 8080)

## Running Tests

The application includes automated tests to verify the REST endpoint functionality.

### Run All Tests

```bash
mvn test
```

### Run Specific Test Class

```bash
mvn test -Dtest=SimpleDataRouteTest
```

### Run Tests with Test Profile (recommended)

```bash
mvn test -Dtest=SimpleDataRouteTest -Dquarkus.profile=test
```

### Clean Build and Test

```bash
mvn clean test
```

### Expected Test Output

```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The tests verify:
- REST endpoint accepts valid JSON patient data
- Endpoint returns proper response structure with `status` and `message` fields
- Application handles different patient data correctly

Tests run in about 5-10 seconds and don't require a running Kafka instance.

## SSL/TLS Configuration for OpenShift Kafka

When connecting to a Kafka cluster on OpenShift with SSL/TLS enabled:

### 1. Extract the Kafka CA Certificate

```bash
oc get secret fhir-cluster-cluster-ca-cert -n fhir \
  -o jsonpath='{.data.ca\.crt}' | base64 -d > src/main/resources/ca.crt
```

### 2. Create a Truststore

```bash
cd src/main/resources
keytool -importcert -alias kafka-ca -file ca.crt \
  -keystore kafka-truststore.jks -storepass changeit -noprompt
```

### 3. Update Application Properties

The application is configured to use the truststore for SSL connections:

```properties
kafka.bootstrap.servers=your-kafka-bootstrap-url:443
camel.component.kafka.security-protocol=SSL
camel.component.kafka.additional-properties[ssl.truststore.location]=/path/to/kafka-truststore.jks
camel.component.kafka.additional-properties[ssl.truststore.password]=changeit
camel.component.kafka.additional-properties[ssl.truststore.type]=JKS
camel.component.kafka.additional-properties[ssl.endpoint.identification.algorithm]=
```

## Architecture

The application uses:

- **Camel Quarkus** for routing
- **Platform HTTP** for REST endpoints
- **Kafka component** for message publishing
- **Kamelets** for reusable integration patterns
- **SSL/TLS** for secure Kafka connections

The flow:

1. REST endpoint receives POST at `/api/data`
2. Data is logged
3. Message is sent to Kafka topic `fhir-data` over SSL/TLS
4. Success/error response returned to client (with explicit logging)
