# Platform Operator

The Platform Operator is the main Kubernetes operator for Platform Lab.

It translates higher-level developer-facing platform resources into lower-level Kubernetes resources.

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

It also keeps those generated resources synchronized when the Application changes.

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
Deployment management
Service management
Application updates
basic Application status
Kubernetes deployment
RBAC
API validation testing
controller integration testing
```

The operator intentionally does not yet support:

```text
workload readiness
Ready conditions
ConfigMaps
Secrets
health checks
resource requests
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

Deployment management is handled by:

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

Service management is handled by:

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

# Application Updates

The Application API is not treated as a one-time provisioning request.

It represents desired state.

For example, an Application may initially be:

```yaml
spec:
  image: greeting-service:1.0.0
  replicas: 1

  port:
    containerPort: 8080
```

The operator creates:

```text
Deployment
image = 1.0.0
replicas = 1
port = 8080

Service
port = 8080
targetPort = 8080
```

Now the Application can be changed to:

```yaml
spec:
  image: greeting-service:2.0.0
  replicas: 3

  port:
    containerPort: 9090
```

The Platform Operator reconciles the existing resources toward the new desired state.

The result becomes:

```text
Deployment
image = 2.0.0
replicas = 3
port = 9090

Service
port = 9090
targetPort = 9090
```

---

# Update Flow

The update lifecycle is:

```text
Application generation 1
        |
        v
Deployment + Service
        |
        | Application spec changes
        v
Application generation 2
        |
        v
reconcile
        |
        +------ Deployment updated
        |
        +------ Service updated
```

The controller does not need special logic like:

```text
if image changed
    update Deployment

if replicas changed
    update Deployment

if port changed
    update Deployment and Service
```

The dependent resources simply recalculate the desired state from the latest Application specification.

---

# Desired-State Reconciliation

The important model is:

```text
current Application spec
        |
        v
desired Deployment
desired Service
        |
        v
compare with actual state
        |
        v
reconcile differences
```

So both creation and update use the same desired-state logic.

For creation:

```text
desired exists
actual missing
      |
      v
create
```

For update:

```text
desired changed
actual exists but differs
      |
      v
update
```

This is one of the main reasons for using managed dependent resources.

---

# Updating an Application

A sample update can be triggered with:

```bash
task application:update
```

The current development task updates the sample Application to:

```yaml
spec:
  image: greeting-service:2.0.0
  replicas: 3

  port:
    containerPort: 9090
```

The same update can also be applied directly:

```bash
kubectl patch application greeting-service \
  --type=merge \
  -p '{
    "spec": {
      "image": "greeting-service:2.0.0",
      "replicas": 3,
      "port": {
        "containerPort": 9090
      }
    }
  }'
```

---

# Generation

When the Application `spec` changes, Kubernetes increments:

```text
metadata.generation
```

For example:

```text
generation = 1
```

before the update may become:

```text
generation = 2
```

after the update.

This indicates that the desired configuration changed.

---

# Application Status

The controller currently writes:

```yaml
status:
  observedGeneration: 2
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

# observedGeneration

The controller records:

```text
status.observedGeneration
```

to indicate which Application generation it has processed.

For example:

```text
metadata.generation       = 2
status.observedGeneration = 2
```

means the controller status represents the latest desired state.

This gives us an important reconciliation invariant:

```text
generation
=
desired state version
```

and:

```text
observedGeneration
=
version processed by the controller
```

---

# Updating Existing Resources

Application updates are expected to modify the existing Deployment and Service rather than replacing them.

The integration test verifies this using Kubernetes resource UIDs.

Before the update:

```text
Deployment UID = A
Service UID    = B
```

After the update:

```text
Deployment UID = A
Service UID    = B
```

The specification changed, but the Kubernetes resources remained the same objects.

Conceptually:

```text
Application update
       |
       v
Deployment patch/update
Service patch/update
```

rather than:

```text
delete Deployment
create Deployment

delete Service
create Service
```

---

# Service Identity

The controller test also records:

```text
Service.spec.clusterIP
```

before and after an Application update.

The value should remain unchanged during a normal reconciliation.

This is another useful check that the Service is being updated in place rather than recreated unnecessarily.

---

# Readiness Is Not Implemented Yet

The Application CRD already allows future fields such as:

```text
readyReplicas
conditions
```

but the controller does not populate them yet.

This is intentional.

Creating or updating a Deployment does not automatically mean:

```text
Application Ready=True
```

For example, a Deployment could exist while Pods are failing because of:

```text
ImagePullBackOff
CrashLoopBackOff
failed readiness probe
scheduling failure
```

Application updates also introduce Deployment rollout behavior:

```text
old ReplicaSet
      |
      v
new ReplicaSet
      |
      v
new Pods starting
      |
      v
old Pods terminating
```

The platform should observe that lifecycle before deciding whether an Application is actually ready.

Readiness will therefore be implemented separately.

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

---

# Running Inside Kubernetes

The Platform Operator itself runs as a Kubernetes Deployment.

The manifests are located under:

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

# Application Commands

Install the Application CRD:

```bash
task application:crd:install
```

Create the sample Application:

```bash
task application:create
```

List Applications:

```bash
task application:get
```

Describe the sample Application:

```bash
task application:describe
```

Update the sample Application:

```bash
task application:update
```

Delete:

```bash
task application:delete
```

---

# Verify Generated Resources

After creating the sample Application:

```bash
task application:create
```

check:

```bash
kubectl get deployment greeting-service
```

and:

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

Inspect Application status:

```bash
kubectl get application greeting-service -o yaml
```

---

# Verifying an Update

Before updating, check the current Deployment:

```bash
kubectl get deployment greeting-service \
  -o jsonpath='{.spec.replicas}'
```

Check the image:

```bash
kubectl get deployment greeting-service \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

Then:

```bash
task application:update
```

Check again:

```bash
kubectl get deployment greeting-service \
  -o jsonpath='{.spec.replicas}'
```

Expected:

```text
3
```

Check image:

```bash
kubectl get deployment greeting-service \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

Expected:

```text
greeting-service:2.0.0
```

Check Service port:

```bash
kubectl get service greeting-service \
  -o jsonpath='{.spec.ports[0].port}'
```

Expected:

```text
9090
```

---

# Testing

Testing is introduced alongside platform features rather than added later.

There are currently two Application test layers.

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

The test verifies the Application CRD contract using the real Kubernetes API server.

It checks:

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

# Application Controller Test

Located at:

```text
scripts/tests/application-controller-test.sh
```

Run:

```bash
task application:test:controller
```

This is a black-box integration test using:

```text
real Kind cluster
real Kubernetes API
real Platform Operator
real Application resource
real Deployment
real Service
```

---

# Controller Test: Creation

The first part of the controller test verifies:

```text
Application created
        |
        v
Deployment created
        |
        v
Deployment matches spec
```

and:

```text
Application created
        |
        v
Service created
        |
        v
Service matches spec
```

It checks:

```text
Deployment replicas

Deployment image

Deployment container port

Service port

Service targetPort

Application deploymentName

Application serviceName
```

---

# Controller Test: Updates

The controller test now also updates the Application.

Initial state:

```yaml
spec:
  image: example/test:1.0.0
  replicas: 2

  port:
    containerPort: 8080
```

Updated state:

```yaml
spec:
  image: example/test:2.0.0
  replicas: 3

  port:
    containerPort: 9090
```

The test waits until the generated resources converge to the new desired state.

It verifies:

```text
Deployment replicas = 3

Deployment image = example/test:2.0.0

Deployment port = 9090

Service port = 9090

Service targetPort = 9090
```

---

# Controller Test: Resource Identity

Before the Application is updated, the test captures:

```text
Deployment UID
Service UID
Service clusterIP
```

After reconciliation, it verifies that those values remain unchanged.

The test therefore proves:

```text
Application update
        |
        v
existing resources updated
```

rather than:

```text
Application update
        |
        v
resources deleted and recreated
```

---

# Controller Test: observedGeneration

The integration test also waits until:

```text
metadata.generation
```

matches:

```text
status.observedGeneration
```

For example:

```text
metadata.generation       = 2
status.observedGeneration = 2
```

This verifies that the operator has processed the latest Application specification.

---

# Test Isolation

Controller tests use a temporary namespace.

For example:

```text
platform-test-12345
```

The test creates:

```text
Application
Deployment
Service
```

inside that namespace.

At the end:

```text
namespace deleted
      |
      v
all test resources removed
```

This keeps test runs isolated and avoids polluting the default namespace.

---

# Current Provisioning and Update Flow

The current platform lifecycle is:

```text
Developer
    |
    v
Application
    |
    v
Platform Operator
    |
    +----------+
    |          |
    v          v
Deployment   Service
    ^          ^
    |          |
    +----------+
         |
Application updates
```

More explicitly:

```text
Application created
        |
        v
resources created
        |
        v
Application updated
        |
        v
same resources reconciled
        |
        v
new desired state applied
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
               ^
               |
               |
        Application update
               |
               v
       desired state recalculated
```

The Application is now a true desired-state API rather than a one-time provisioning request.

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

Inspect resources:

```bash
kubectl get applications

kubectl get deployments

kubectl get services
```

Update the Application:

```bash
task application:update
```

Run API tests:

```bash
task application:test:api
```

Run controller lifecycle tests:

```bash
task application:test:controller
```

---

# What This Version Proves

The current implementation and tests prove:

```text
Application API can be validated

Application creates Deployment

Application creates Service

generated resources match Application spec

Application updates are reconciled

Deployment is updated in place

Service is updated in place

Service identity remains stable

observedGeneration catches up with generation
```

It does not yet prove:

```text
Pods become healthy

Application becomes Ready

Deployment rollout completes

failed rollout is reported

deleted dependent resources are repaired

Application deletion cleans up resources

configuration works

Secrets work
```

Those will be introduced separately.

---

# What's Next?

The next important topic is workload readiness.

Right now the operator can say:

```text
I created or updated the Deployment.
```

But it cannot yet say:

```text
The application is actually ready.
```

A Deployment update may result in:

```text
new ReplicaSet
      |
      v
new Pods
      |
      +---- Running
      |
      +---- Pending
      |
      +---- ImagePullBackOff
      |
      +---- CrashLoopBackOff
```

So the next step is to observe Deployment status and define what:

```text
Application Ready
```

actually means.

That will likely introduce:

```text
readyReplicas
Ready condition
Progressing condition
Deployment status observation
rollout-aware reconciliation
```

After that, the platform can continue toward:

```text
configuration
Secrets
health checks
ApplicationRelease
ApiDependency
AccessGrant
networking
authorization
```

one capability at a time.
