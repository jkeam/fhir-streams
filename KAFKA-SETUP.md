# Kafka Setup Guide

This application can connect to either a local Kafka instance or an OpenShift Kafka cluster.

## Local Kafka Setup (for development)

### Start Local Kafka

**Option 1: Using Docker Compose (easiest)**
```bash
docker compose up -d
```

**Option 2: Using Podman/Docker directly**
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
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk \
  apache/kafka:latest
```

### Configure Application for Local Kafka

Edit `src/main/resources/application.properties`:

```properties
# HTTP Configuration
quarkus.http.port=8080

# Kafka Configuration - LOCAL
kafka.bootstrap.servers=localhost:9092

# Camel Kafka Configuration - PLAINTEXT (no SSL for local)
camel.component.kafka.security-protocol=PLAINTEXT

# Camel Configuration
camel.context.name=fhir-streams-context

# Logging
quarkus.log.level=INFO
quarkus.log.category."com.acme".level=DEBUG
```

### Create the Topic

```bash
podman exec -it kafka-local /opt/kafka/bin/kafka-topics.sh \
  --create \
  --bootstrap-server localhost:9092 \
  --topic fhir-data \
  --partitions 1 \
  --replication-factor 1
```

### Test the Application

Send a test message:
```bash
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{"patient":"12345","status":"active","timestamp":"2026-07-03T10:00:00Z"}'
```

Consume from Kafka to verify:
```bash
podman exec -it kafka-local /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic fhir-data \
  --from-beginning
```

### Stop Local Kafka

```bash
# Using Docker Compose
docker compose down

# Using Podman/Docker
podman stop kafka-local
podman rm kafka-local
```

---

## OpenShift Kafka Setup (for production)

### Prerequisites

- Access to an OpenShift cluster with Strimzi Kafka Operator
- `oc` CLI logged in to your cluster
- Kafka cluster deployed (e.g., `fhir-cluster`)

### Extract CA Certificate

```bash
oc get secret fhir-cluster-cluster-ca-cert -n fhir \
  -o jsonpath='{.data.ca\.crt}' | base64 -d > src/main/resources/ca.crt
```

### Create Truststore

```bash
cd src/main/resources
keytool -importcert -alias kafka-ca -file ca.crt \
  -keystore kafka-truststore.jks -storepass changeit -noprompt
```

### Configure Application for OpenShift Kafka

Edit `src/main/resources/application.properties`:

```properties
# HTTP Configuration
quarkus.http.port=8080

# Kafka Configuration - OPENSHIFT
kafka.bootstrap.servers=fhir-cluster-kafka-tls-bootstrap-fhir.apps.clustername.host.com:443

# Camel Kafka TLS Configuration with truststore
camel.component.kafka.security-protocol=SSL
camel.component.kafka.additional-properties[ssl.truststore.location]=src/main/resources/kafka-truststore.jks
camel.component.kafka.additional-properties[ssl.truststore.password]=changeit
camel.component.kafka.additional-properties[ssl.truststore.type]=JKS
camel.component.kafka.additional-properties[ssl.endpoint.identification.algorithm]=

# Camel Configuration
camel.context.name=fhir-streams-context

# Logging
quarkus.log.level=INFO
quarkus.log.category."com.acme".level=DEBUG
```

### Create Kafka Topic in OpenShift

Create a file `openshift/kafka/fhir-data-topic.yaml`:

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: fhir-data
  labels:
    strimzi.io/cluster: fhir-cluster
spec:
  partitions: 3
  replicas: 3
  config:
    retention.ms: 604800000  # 7 days
    segment.bytes: 1073741824
```

Apply it:
```bash
oc apply -f openshift/kafka/fhir-data-topic.yaml -n fhir
```

---

## Quick Switch Between Environments

### Switch to Local

1. Comment out OpenShift Kafka config in `application.properties`
2. Uncomment local Kafka config
3. Start local Kafka: `docker compose up -d`
4. Restart your app: `mvn quarkus:dev`

### Switch to OpenShift

1. Comment out local Kafka config in `application.properties`
2. Uncomment OpenShift Kafka config (with SSL)
3. Ensure truststore exists: `ls src/main/resources/kafka-truststore.jks`
4. Restart your app: `mvn quarkus:dev`

---

## Troubleshooting

### Local Kafka

**Check if Kafka is running:**
```bash
podman ps | grep kafka
nc -zv localhost 9092
```

**View Kafka logs:**
```bash
podman logs -f kafka-local
```

**List topics:**
```bash
podman exec -it kafka-local /opt/kafka/bin/kafka-topics.sh \
  --list --bootstrap-server localhost:9092
```

### OpenShift Kafka

**Check Kafka cluster status:**
```bash
oc get kafka fhir-cluster -n fhir
oc get pods -n fhir | grep kafka
```

**Check topic:**
```bash
oc get kafkatopic fhir-data -n fhir
```

**View application logs:**
```bash
# In dev mode, logs are in the terminal
# Check for "SUCCESS: Message sent to Kafka topic fhir-data"
```
