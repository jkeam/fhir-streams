# FHIR Streams

## Prerequisites

1. OpenShift 4.20+
2. Streams for Apache Kafka v3.20+

## Setup

### Local

Run kafka locally.

```shell
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

Then start server

```shell
# can replace `dev` with `prod`
# will default with `dev` if you leave this off
mvn quarkus:dev -Dquarkus.profile=dev
```

### OpenShift

```shell
oc new-project fhir
oc apply -k openshift/kafka
```

Once everything comes up, we need to populate our cert store.

```shell
cd ./src/main/resources
# ca.crt
oc get secret fhir-cluster-cluster-ca-cert -n fhir -o jsonpath='{.data.ca\.crt}' | base64 -d > ./ca.crt
# kafka-truststore.jks
keytool -importcert -alias kafka-ca -file ./ca.crt -keystore kafka-truststore.jks -storepass changeit -noprompt
```

## Testing

### Unit Testing

```shell
mvn test
```

### Manual Testing

Start the app and POST the following.

```shell
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{"patient":"12345","status":"active","timestamp":"2026-07-02T10:00:00Z"}'
```

## Docs

All extra docs are in the `docs` directory.

1. README-SETUP.md - Main readme describing this project, how to build, and how to test
2. KAFKA-SETUP.md - Kafka is needed for this project, this readme helps set that up
3. RUNNING.md - Explains the difference between running in different modes, dev, test, and prod
