# Platform Operator

The Platform Operator is the main Kubernetes operator for Platform Lab.

It is responsible for translating higher-level developer-facing platform resources into lower-level Kubernetes resources.

The first platform API is:

```text
Application
```

An `Application` currently describes:

```text
image
replicas
container port
```

The Platform Operator translates that into:

```text
Application
     |
     v
Platform Operator
     |
     +------ Deployment
     |
     +------ Service
```

This is the first real platform behavior in the project.

---

# Goal

The goal of Platform Lab is to reduce the amount of Kubernetes configuration an application developer needs to manage directly.

Instead of requiring developers to define resources such as:

```text
Deployment
Service
ConfigMap
Secret
ServiceAccount
NetworkPolicy
AuthorizationPolicy
```

the platform should expose simpler APIs.

For example:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Application

metadata:
  name: greeting-service

spec:
  image: greeting-service:1.0.0

  replicas: 1

  port:
    containerPort: 8080
```

The Platform Operator handles the Kubernetes implementation behind that API.

---

# Current Scope

The current Platform Operator supports:

```text
Application CRD
Application reconciliation
Deployment creation
Service creation
basic Application status
Kubernetes deployment
RBAC
controller integration testing
```

The operator intentionally does not yet support:

```text
workload readiness
conditions
ConfigMaps
Secrets
health checks
resource limits
routes
Istio
authorization
API dependencies
ApplicationRelease
```

Those capabilities will be added incrementally when they have a real platform use case.

---

# Application API

The Application API uses:

```text
Group:   platform.shubforge.dev
Version: v1alpha1
Kind:    Application
```

The CRD is defined in:

```text
k8s/crds/applications.yaml
```

The resource is namespace scoped.

---

# Application Specification

The current specification is intentionally small.

Example:

```yaml
spec:
  image: greeting-service:1.0.0

  replicas: 1

  port:
    containerPort: 8080
```

The supported fields are:

```text
image
replicas
port.containerPort
```

---

## image

The container image to run.

Example:

```yaml
image: greeting-service:1.0.0
```

`image` is required.

---

## replicas

The desired number of application replicas.

Example:

```yaml
replicas: 2
```

The current API requires:

```text
replicas >= 1
```

The default is:

```text
1
```

---

## port

The initial API supports one application port.

Example:

```yaml
port:
  containerPort: 8080
```

The CRD validates that the port is between:

```text
1
```

and:

```text
65535
```

---

# Why the API Is Small

The first Application API deliberately avoids exposing too many Kubernetes concepts.

The developer currently does not need to define:

```text
Deployment selectors
Pod templates
Service selectors
targetPort
ReplicaSets
managed labels
```

The platform creates those details.

The intention is:

```text
Developer API
     |
     v
small and application focused
```

while:

```text
Platform implementation
     |
     v
Kubernetes specific
```

---

# Application Controller

The main controller is:

```text
ApplicationReconciler.java
```

It watches:

```text
Application
```

resources.

The current controller workflow is:

```text
Application
     |
     v
ApplicationReconciler
     |
     +----------------+
     |                |
     v                v
Deployment         Service
```

The Deployment and Service are modeled as managed dependent resources.

---

# Java Resource Model

The Application custom resource is represented by:

```text
Application.java
ApplicationSpec.java
ApplicationPort.java
ApplicationStatus.java
```

The mapping is:

```text
Application
    |
    +---- ApplicationSpec
    |
    +---- ApplicationStatus
```

with:

```text
ApplicationSpec
    |
    +---- image
    +---- replicas
    +---- ApplicationPort
```

---

# Deployment Dependent Resource

Deployment creation is handled by:

```text
ApplicationDeploymentDependentResource.java
```

The platform converts:

```yaml
spec:
  image: greeting-service:1.0.0

  replicas: 1

  port:
    containerPort: 8080
```

into a Deployment roughly equivalent to:

```yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: greeting-service

spec:
  replicas: 1

  selector:
    matchLabels:
      platform.shubforge.dev/application: greeting-service

  template:

    metadata:
      labels:
        platform.shubforge.dev/application: greeting-service

    spec:
      containers:
        - name: application
          image: greeting-service:1.0.0

          ports:
            - containerPort: 8080
```

The operator also adds:

```text
app.kubernetes.io/managed-by=platform-operator
```

to managed resources.

---

# Service Dependent Resource

Service creation is handled by:

```text
ApplicationServiceDependentResource.java
```

The Service uses the same application label used by the Deployment pod template.

Conceptually:

```text
Application
      |
      v
Deployment
      |
      v
Pods
      ^
      |
Service selector
```

The generated Service is roughly:

```yaml
apiVersion: v1
kind: Service

metadata:
  name: greeting-service

spec:

  selector:
    platform.shubforge.dev/application: greeting-service

  ports:
    - name: http
      port: 8080
      targetPort: 8080
      protocol: TCP
```

This allows the Service to target the Pods created by the generated Deployment.

---

# Labels

The Platform Operator currently uses:

```text
platform.shubforge.dev/application
```

to connect application resources.

For example:

```yaml
platform.shubforge.dev/application: greeting-service
```

This label is used by:

```text
Deployment selector
Pod template labels
Service selector
```

The relationship is:

```text
Application
      |
      v
platform.shubforge.dev/application=greeting-service
      |
      +------ Deployment Pods
      |
      +------ Service selector
```

---

# Managed Resources

The Deployment and Service are implemented using Java Operator SDK managed dependent resources.

The Platform Operator describes the desired Kubernetes objects.

Conceptually:

```text
Application spec
      |
      v
desired Deployment
      |
      v
actual Deployment
```

and:

```text
Application spec
      |
      v
desired Service
      |
      v
actual Service
```

The controller does not manually implement separate:

```text
create
update
delete
```

logic for each resource.

Instead, reconciliation continuously moves the actual resources toward their desired state.

---

# Application Status

The controller currently writes a small amount of status information.

Example:

```yaml
status:
  observedGeneration: 1
  deploymentName: greeting-service
  serviceName: greeting-service
```

The current status fields are:

```text
observedGeneration
deploymentName
serviceName
```

---

## observedGeneration

The controller records:

```yaml
status:
  observedGeneration:
```

to show which Application generation it has processed.

For example:

```text
metadata.generation      = 1
status.observedGeneration = 1
```

means the current controller status corresponds to the current desired Application specification.

---

# Readiness Is Not Implemented Yet

The Application CRD already allows future fields such as:

```text
readyReplicas
conditions
```

but the controller does not populate them yet.

This is intentional.

Creating a Deployment successfully does not automatically mean:

```text
Application Ready=True
```

For example, the Deployment could exist but the workload could be failing because of:

```text
ImagePullBackOff
CrashLoopBackOff
failed readiness probe
scheduling failure
```

So readiness will be implemented separately once the operator starts observing Deployment status.

For the current version:

```text
resource provisioned
```

does not yet mean:

```text
application ready
```

---

# Operator Entry Point

The operator is started by:

```text
PlatformOperatorApplication.java
```

It creates the Java Operator SDK `Operator` and registers:

```text
ApplicationReconciler
```

Conceptually:

```text
PlatformOperatorApplication
          |
          v
       Operator
          |
          v
ApplicationReconciler
```

Future platform controllers will be registered in the same operator.

For example:

```text
Platform Operator
      |
      +---- ApplicationReconciler
      |
      +---- ApplicationReleaseReconciler
      |
      +---- ApiDependencyReconciler
      |
      +---- AccessGrantReconciler
```

This is why the module is called:

```text
platform-operator
```

rather than:

```text
application-operator
```

---

# Running Inside Kubernetes

The Platform Operator itself runs as a Kubernetes Deployment.

The Kubernetes manifests are located under:

```text
k8s/platform-operator/
```

Current resources:

```text
Namespace
ServiceAccount
ClusterRole
ClusterRoleBinding
Deployment
```

---

# ServiceAccount

The operator runs using:

```text
platform-system/platform-operator
```

as its ServiceAccount.

---

# RBAC

The Platform Operator currently needs permissions for:

```text
Applications
Application status
Deployments
Services
```

The authorization chain is:

```text
Platform Operator Pod
        |
        v
ServiceAccount
        |
        v
ClusterRoleBinding
        |
        v
ClusterRole
        |
        v
Kubernetes API
```

Permissions will grow only when new platform capabilities require them.

---

# Docker Image

The operator is packaged into:

```text
platform-operator:dev
```

for local development.

The image is loaded into the local Kind cluster.

The basic flow is:

```text
Maven package
      |
      v
Docker build
      |
      v
platform-operator:dev
      |
      v
kind load
      |
      v
Platform Operator Deployment
```

---

# Build

From the repository root:

```bash
task platform-operator:build
```

This builds:

```text
operators/platform-operator
```

using the repository Maven wrapper.

---

# Build the Docker Image

```bash
task platform-operator:image:build
```

---

# Load the Image into Kind

```bash
task platform-operator:image:load
```

---

# Deploy the Operator

```bash
task platform-operator:deploy
```

---

# Check Operator Status

```bash
task platform-operator:status
```

---

# View Logs

```bash
task platform-operator:logs
```

---

# Restart

```bash
task platform-operator:restart
```

---

# Creating an Application

Install the CRD first:

```bash
task application:crd:install
```

Create the sample Application:

```bash
task application:create
```

Check:

```bash
task application:get
```

The sample resource is:

```text
k8s/samples/application.yaml
```

---

# Verify Generated Resources

Once the Platform Operator is running, creating:

```text
Application/greeting-service
```

should create:

```text
Deployment/greeting-service
Service/greeting-service
```

Check the Deployment:

```bash
kubectl get deployment greeting-service
```

Check the Service:

```bash
kubectl get service greeting-service
```

Inspect the Deployment:

```bash
kubectl get deployment greeting-service -o yaml
```

Inspect the Service:

```bash
kubectl get service greeting-service -o yaml
```

Check Application status:

```bash
kubectl get application greeting-service -o yaml
```

---

# Current Provisioning Flow

The current provisioning flow is:

```text
Developer
    |
    v
Application YAML
    |
    v
Kubernetes API
    |
    v
Application Custom Resource
    |
    v
ApplicationReconciler
    |
    +---------------+
    |               |
    v               v
Deployment        Service
```

The developer only defines the `Application`.

The platform owns the Kubernetes implementation.

---

# Testing

Testing is being introduced alongside platform features rather than added later.

There are currently two levels of Application tests.

---

## Application API Test

Located at:

```text
scripts/tests/application-api-test.sh
```

Run:

```bash
task application:test:api
```

This test verifies the Application CRD contract using the real Kubernetes API server.

It tests:

```text
valid Application          → accepted

missing image              → rejected

invalid container port     → rejected

invalid replica count      → rejected
```

It uses:

```bash
kubectl apply --dry-run=server
```

so API validation happens inside Kubernetes without persisting the test resources.

---

## Application Controller Test

Located at:

```text
scripts/tests/application-controller-test.sh
```

Run:

```bash
task application:test:controller
```

This is a black-box integration test.

It creates:

```text
temporary namespace
      |
      v
Application
      |
      v
running Platform Operator
      |
      v
Deployment + Service
```

The test verifies:

```text
Application is created

Deployment appears

Deployment replicas match the Application

Deployment image matches the Application

Deployment container port matches the Application

Service appears

Service port matches the Application

Application status contains the generated resource names
```

The temporary test namespace is removed when the test completes.

---

# Why the Controller Test Uses a Real Operator

The current controller test intentionally exercises the full local platform flow.

```text
test script
     |
     v
real Kubernetes API
     |
     v
real Application CR
     |
     v
real Platform Operator Pod
     |
     v
real reconciler
     |
     v
real Deployment + Service
```

This provides an easy-to-understand first integration test.

Java-level tests can be introduced later where they provide faster or more focused feedback.

---

# Test Isolation

Controller tests use a temporary namespace.

Conceptually:

```text
platform-test-12345
```

The test creates its Application and generated resources in that namespace.

At the end:

```text
namespace deleted
      |
      v
all test resources removed
```

This avoids polluting:

```text
default
```

with integration-test resources.

---

# Current Project Structure

```text
operators/platform-operator/
├── Dockerfile
├── pom.xml
├── README.md
│
└── src/
    └── main/
        └── java/
            └── dev/shubforge/platform/application/
                ├── Application.java
                ├── ApplicationSpec.java
                ├── ApplicationPort.java
                ├── ApplicationStatus.java
                ├── ApplicationReconciler.java
                ├── ApplicationDeploymentDependentResource.java
                ├── ApplicationServiceDependentResource.java
                └── PlatformOperatorApplication.java
```

Related Kubernetes resources:

```text
k8s/
├── crds/
│   └── applications.yaml
│
├── platform-operator/
│   ├── namespace.yaml
│   ├── service-account.yaml
│   ├── rbac.yaml
│   └── deployment.yaml
│
└── samples/
    └── application.yaml
```

Tests:

```text
scripts/
└── tests/
    ├── application-api-test.sh
    └── application-controller-test.sh
```

---

# Current Architecture

```text
                       Application
                            |
                            v
                  ApplicationReconciler
                            |
             +--------------+--------------+
             |                             |
             v                             v
        Deployment                      Service
             |                             |
             v                             |
            Pods <-------------------------+
```

The `Application` resource is now the source of desired state.

The operator translates that higher-level desired state into Kubernetes resources.

---

# Current Development Workflow

A typical local flow is:

```bash
task cluster:create

task application:crd:install

task platform-operator:image:build

task platform-operator:image:load

task platform-operator:deploy

task application:create
```

Then inspect:

```bash
kubectl get applications

kubectl get deployments

kubectl get services
```

Run API tests:

```bash
task application:test:api
```

Run controller tests:

```bash
task application:test:controller
```

---

# What This Version Does Not Prove Yet

The current controller test proves:

```text
Application
→ resources are provisioned correctly
```

It does not yet prove:

```text
Pods become healthy

Application becomes Ready

Application updates propagate correctly

deleted resources are recreated

Application deletion removes managed resources

failure status works

retry behavior works
```

These will be added incrementally as those capabilities are implemented.

---

# What's Next?

The next important behavior is Application updates.

For example:

```yaml
spec:
  image: greeting-service:2.0.0
  replicas: 3
```

should update the existing Deployment rather than create another one.

The next lifecycle should look like:

```text
Application generation 1
        |
        v
Deployment replicas = 1
image = 1.0.0

        |
        | Application updated
        v

Application generation 2
        |
        v
same Deployment
replicas = 3
image = 2.0.0
```

The matching integration test should verify the same behavior.

After that, the platform can move toward:

```text
Application readiness
configuration
Secrets
ApplicationRelease
API dependencies
Access grants
networking
authorization
```

Capabilities will continue to be added only when the platform has a concrete use case for them.
