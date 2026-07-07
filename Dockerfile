####
# Multi-stage Dockerfile for Quarkus application
####

## Stage 1: Build the application
FROM registry.access.redhat.com/ubi9/openjdk-17:1.20 AS build

USER root

# Copy pom.xml
COPY --chown=default:root ./pom.xml /code/

# Download dependencies
WORKDIR /code
RUN mvn dependency:go-offline -B

# Copy source code
COPY --chown=default:root ./src /code/src

# Build the application
RUN mvn package -DskipTests -Dquarkus.package.jar.type=uber-jar

## Stage 2: Create the runtime image
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.20

# Set environment variables
ENV LANGUAGE='en_US:en'

# Copy app
COPY --from=build --chown=185 /code/target/fhir-streams-1.0.0-SNAPSHOT-runner.jar /deployments

# Copy truststore for Kafka SSL
COPY --chown=185 ./src/main/resources/kafka-truststore.jks /deployments/config/kafka-truststore.jks

# Expose application port
EXPOSE 8080

# Set user to non-root (OpenShift requirement)
USER 185

# Set the Java options
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/fhir-streams-1.0.0-SNAPSHOT-runner.jar"

# Run the application
ENTRYPOINT [ "java", "-jar", "/deployments/fhir-streams-1.0.0-SNAPSHOT-runner.jar" ]
