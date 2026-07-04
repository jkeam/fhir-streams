# OpenShift Deployment Guide

This guide covers deploying the FHIR Streams application to OpenShift.

## Prerequisites

- OpenShift CLI (`oc`) installed and configured
- Access to an OpenShift cluster
- Kafka cluster already deployed (see `kafka/` directory)

## Deployment Options

### Option 1: Using OpenShift BuildConfig (Recommended)

This approach builds the container image directly in OpenShift.

1. **Create/switch to your namespace:**
   ```bash
   oc new-project fhir
   # or
   oc project fhir
   ```

2. **Update BuildConfig with your Git repository:**
   Edit `buildconfig.yaml` and update the Git URL:
   ```yaml
   git:
     uri: https://github.com/YOUR_USERNAME/fhir-streams.git
     ref: main
   ```

3. **Deploy using Kustomize:**
   ```bash
   oc apply -k openshift/
   ```

4. **Start the build:**
   ```bash
   oc start-build fhir-streams --follow
   ```

5. **Monitor deployment:**
   ```bash
   oc get pods -w
   ```

6. **Get the route URL:**
   ```bash
   oc get route fhir-streams
   ```

### Option 2: Using Local Docker Build

Build the image locally and push to OpenShift's internal registry.

1. **Login to OpenShift:**
   ```bash
   oc login
   oc project fhir
   ```

2. **Build the image locally:**
   ```bash
   docker build -t fhir-streams:latest .
   ```

3. **Get OpenShift registry URL:**
   ```bash
   oc registry info
   # Example: default-route-openshift-image-registry.apps.cluster.example.com
   ```

4. **Login to OpenShift registry:**
   ```bash
   docker login -u $(oc whoami) -p $(oc whoami -t) $(oc registry info)
   ```

5. **Tag and push the image:**
   ```bash
   docker tag fhir-streams:latest $(oc registry info)/fhir/fhir-streams:latest
   docker push $(oc registry info)/fhir/fhir-streams:latest
   ```

6. **Deploy (skip ImageStream and BuildConfig):**
   ```bash
   oc apply -f openshift/deployment.yaml
   oc apply -f openshift/service.yaml
   oc apply -f openshift/route.yaml
   ```

### Option 3: Using Quarkus OpenShift Extension

The project includes the `quarkus-openshift` extension, so you can deploy directly:

1. **Login to OpenShift:**
   ```bash
   oc login
   oc project fhir
   ```

2. **Build and deploy:**
   ```bash
   ./mvnw clean package -Dquarkus.kubernetes.deploy=true
   ```

## Verification

1. **Check pod status:**
   ```bash
   oc get pods
   oc logs -f deployment/fhir-streams
   ```

2. **Test health endpoints:**
   ```bash
   ROUTE=$(oc get route fhir-streams -o jsonpath='{.spec.host}')
   curl https://$ROUTE/q/health/live
   curl https://$ROUTE/q/health/ready
   ```

3. **Test the application:**
   ```bash
   curl https://$ROUTE/fhir
   ```

## Configuration

### Environment Variables

The deployment uses these key environment variables:

- `QUARKUS_PROFILE=prod` - Activates production profile
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka cluster address

To override configuration:

```bash
oc set env deployment/fhir-streams KEY=VALUE
```

### Scaling

```bash
# Scale up
oc scale deployment/fhir-streams --replicas=3

# Auto-scaling
oc autoscale deployment/fhir-streams --min=2 --max=5 --cpu-percent=80
```

## Troubleshooting

### Check logs:
```bash
oc logs -f deployment/fhir-streams
```

### Check events:
```bash
oc get events --sort-by='.lastTimestamp'
```

### Debug pod:
```bash
oc debug deployment/fhir-streams
```

### Check Kafka connectivity:
```bash
oc rsh deployment/fhir-streams
# Inside the pod:
curl -v telnet://fhir-cluster-kafka-tls-bootstrap-fhir.apps.rosa.dha-test.zu23.p3.openshiftapps.com:443
```

## Cleanup

```bash
# Delete all resources
oc delete -k openshift/

# Or delete individually
oc delete deployment,service,route,imagestream,buildconfig -l app=fhir-streams
```

## Security Notes

- The application runs as non-root user (UID 185)
- TLS is enabled for the route (edge termination)
- Security context is restricted for OpenShift compliance
- Kafka communication uses SSL/TLS with truststore
