# FHIR Streams

## Prerequisites

1. OpenShift 4.20+
2. Streams for Apache Kafka v3.20+
3. Streams for Apache Kafka Console v3.20+

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
  docker.io/apache/kafka:latest
```

Then start server

```shell
# can replace `dev` with `prod`
# will default with `dev` if you leave this off
mvn quarkus:dev -Dquarkus.profile=dev
```

### OpenShift

First, update the `hostname` in `./openshift/kafka/kafka-console.yaml`.

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
keytool -importcert -alias kafka-ca -file ./ca.crt -keystore kafka-truststore.jks -storetype JKS -storepass changeit -noprompt
```

Then build and deploy

```shell
podman build -t quay.io/username/fhir-streams -f ./Dockerfile .
podman push -t quay.io/username/fhir-streams
```

Then update `./openshift/app/deployment.yaml` with:

1. `spec.template.spec.containers[?(@.name == 'fhir-streams')].image` with your container image
2. `spec.template.spec.containers[?(@.name == 'fhir-streams')].env[?(@.name == 'KAFKA_BOOTSTRAP_SERVERS')]` with your Kafka Bootstrap URL (see below on how to obtain this)

Use this to get the Kafka Bootstrap URL:

```shell
echo "http://$(oc get routes/fhir-cluster-kafka-tls-bootstrap -o jsonpath='{.spec.host}' -n fhir)"
```

Once everything looks good, deploy the app:

```shell
oc apply -k openshift/app
```

#### Destroying from OpenShift

```shell
oc delete -k ./openshift/app
oc delete -k ./openshift/kafka-dev-spaces
```

### Developing using Dev Spaces

Create a new workspace using the `devfile`.

Once in the dev space workspace, open up a terminal and run:

```shell
oc apply -k ./openshift/kafka-dev-spaces
```

Wait a bit for everything to come up and then let's generate a truststore for it:

```shell
cd ./src/main/resources
rm ./ca.crt ./kafka-truststore.jks
# ca.crt
oc get secret fhir-cluster-cluster-ca-cert -o jsonpath='{.data.ca\.crt}' | base64 -d > ./ca.crt
# kafka-truststore.jks
keytool -importcert -alias kafka-ca -file ./ca.crt -keystore kafka-truststore.jks -storetype JKS -storepass changeit -noprompt
```

Next, find and delete the following from `./src/main/resources/application.properties`:

```properties
%dev.kafka.bootstrap.servers=localhost:9092
%dev.camel.component.kafka.security-protocol=PLAINTEXT
```

and replace with this, making sure to set the `KAFKA_BOOTSTRAP_URL`:

```properties
%dev.camel.component.kafka.security-protocol=SSL
%dev.camel.component.kafka.additional-properties[ssl.truststore.location]=src/main/resources/kafka-truststore.jks
%dev.camel.component.kafka.additional-properties[ssl.truststore.password]=changeit
%dev.camel.component.kafka.additional-properties[ssl.truststore.type]=JKS
%dev.camel.component.kafka.additional-properties[ssl.endpoint.identification.algorithm]=
# replace the following with the real value
%dev.kafka.bootstrap.servers=<$KAFKA_BOOTSTRAP_URL>
```

Use this to find your `KAFKA_BOOTSTRAP_URL`:

```shell
echo "$(oc get routes/fhir-cluster-kafka-tls-bootstrap -o jsonpath='{.spec.host}'):443"
```

Then run `mvn quarkus:dev` from the project root (`cd $PROJECT_SOURCE`) or run the associated `Run Task: Devfile` task.

#### Destroying the Dev Space Workspace

To tear all this down first make sure your server is not running.

Then from the project root, run:

```shell
cd $PROJECT_SOURCE
oc delete -k ./openshift/kafka-dev-spaces
```

Wait a bit for everything to get destroyed and then feel free to delete the dev space workspace.
If things get stuck, check finalizers; in particular the KafkaTopic.

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

Replace `localhost:8080` with your OpenShift route for a deployed app.

## Docs

### Apache Kafka on OpenShift Docs

1. [Streams for Apache Kafka 3.2](https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2)
2. [Getting Started with Streams for Apache Kafka on OpenShift](https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2/html/getting_started_with_streams_for_apache_kafka_on_openshift/index)
3. [Deploying and Managing Streams for Apache Kafka on OpenShift](https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2/html/deploying_and_managing_streams_for_apache_kafka_on_openshift/index)
4. [Using the Streams for Apache Kafka Console](https://docs.redhat.com/en/documentation/red_hat_streams_for_apache_kafka/3.2/html/using_the_streams_for_apache_kafka_console/index)

### AI Generated

All extra docs are in the `docs` directory.

1. README-SETUP.md - Main readme describing this project, how to build, and how to test
2. KAFKA-SETUP.md - Kafka is needed for this project, this readme helps set that up
3. RUNNING.md - Explains the difference between running in different modes, dev, test, and prod
