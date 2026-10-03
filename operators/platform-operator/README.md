# Platform Operator

The Platform Operator is the main Kubernetes operator for Platform Lab.

It translates higher-level developer-facing platform resources into lower-level Kubernetes resources and continuously reflects workload state back into the platform API.

The first platform API is:

```text
Application
```

An `Application` currently describes:

- container image
- replicas
- container port

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

It also observes the generated Deployment and exposes workload readiness through:

```text
Application.status
```

---

## Goal

The goal of Platform Lab is to reduce the amount of Kubernetes configuration an application developer needs to manage directly.

Instead of requiring developers to define resources such as:

- Deployment
- Service
- ConfigMap
- Secret
- ServiceAccount
- NetworkPolicy
- AuthorizationPolicy

the platform should expose simpler, higher-level APIs.

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

The Platform Operator handles the Kubernetes implementation behind this API.

---

## Current Scope

The Platform Operator currently supports:

- Application CRD
- Application reconciliation
- Deployment management
- Service management
- Application updates
- Application status
- `observedGeneration`
- workload readiness
- ready replica reporting
- `Ready` condition
- Kubernetes RBAC
- API validation tests
- Java readiness unit tests
- controller integration tests

The operator intentionally does not yet support:

- ConfigMaps
- Secrets
- health probes
- resource requests and limits in the Application API
- routes
- Istio
- authorization
- API dependencies
- ApplicationRelease
- Pod-level failure reasons

These capabilities will be added incrementally when the platform has a concrete use case for them.

---

## Application API

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

`Application` is a namespaced resource.

---

## Application Specification

The current specification is intentionally small.

```yaml
spec:
  image: greeting-service:1.0.0
  replicas: 1

  port:
    containerPort: 8080
```

The currently supported fields are:

| Field | Description |
| --- | --- |
| `image` | Container image to run |
| `replicas` | Desired number of replicas |
| `port.containerPort` | Port exposed by the application container |

### Image

Example:

```yaml
image: greeting-service:1.0.0
```

`image` is required.

### Replicas

Example:

```yaml
replicas: 3
```

The API currently requires:

```text
replicas >= 1
```

The default value is:

```text
1
```

### Port

The initial API supports one application port.

```yaml
port:
  containerPort: 8080
```

The CRD validates that the port is between:

```text
1 - 65535
```

---

## Why the API Is Small

The developer currently does not need to define Kubernetes-specific configuration such as:

- Deployment selectors
- Pod templates
- Service selectors
- target ports
- ReplicaSets
- managed labels

The platform owns these implementation details.

```text
Developer API
     |
     v
Application-focused
```

while:

```text
Platform implementation
     |
     v
Kubernetes-specific
```

---

## Application Controller

The main controller is:

```text
ApplicationReconciler.java
```

It watches `Application` resources.

The current controller flow is:

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

The Deployment and Service are implemented as managed dependent resources.

---

## Java Resource Model

The Application custom resource is represented by:

```text
Application.java
ApplicationSpec.java
ApplicationPort.java
ApplicationStatus.java
```

The model looks like:

```text
Application
    |
    +---- ApplicationSpec
    |
    +---- ApplicationStatus
```

and:

```text
ApplicationSpec
    |
    +---- image
    +---- replicas
    +---- ApplicationPort
```

---

## Deployment Management

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

Managed resources also contain:

```text
app.kubernetes.io/managed-by=platform-operator
```

---

## Service Management

Service management is handled by:

```text
ApplicationServiceDependentResource.java
```

The generated Service uses the same application label as the Deployment Pods.

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

---

## Application Updates

`Application` is a desired-state API.

It is not a one-time provisioning request.

An Application may initially contain:

```yaml
spec:
  image: greeting-service:1.0.0
  replicas: 1

  port:
    containerPort: 8080
```

and later change to:

```yaml
spec:
  image: greeting-service:2.0.0
  replicas: 3

  port:
    containerPort: 9090
```

The Platform Operator reconciles the existing Deployment and Service toward the new desired state.

```text
Application generation 1
        |
        v
Deployment + Service
        |
        | spec changes
        v
Application generation 2
        |
        v
same Deployment + Service
updated in place
```

---

## Desired-State Reconciliation

Creation and updates use the same reconciliation model.

For creation:

```text
desired resource exists
actual resource missing
        |
        v
create
```

For updates:

```text
desired resource changed
actual resource differs
        |
        v
update
```

The controller does not need separate logic such as:

```text
if image changed
if replicas changed
if port changed
```

The desired Deployment and Service are recalculated from the current `Application.spec`.

---

## Application Status

The Application exposes both provisioning information and workload readiness.

Example:

```yaml
status:
  observedGeneration: 2

  deploymentName: greeting-service
  serviceName: greeting-service

  readyReplicas: 3

  conditions:
    - type: Ready
      status: "True"
      observedGeneration: 2
      reason: DeploymentReady
      message: Deployment has 3/3 ready replicas
      lastTransitionTime: "2026-10-03T14:20:00Z"
```

The current status fields are:

- `observedGeneration`
- `deploymentName`
- `serviceName`
- `readyReplicas`
- `conditions`

---

## Provisioned vs Ready

One important distinction in the Application API is:

```text
Provisioned != Ready
```

The Platform Operator may successfully create:

```text
Deployment
Service
```

while the actual workload is unhealthy.

For example:

```text
Application
      |
      v
Deployment created
      |
      v
Pod created
      |
      v
ImagePullBackOff
```

The Kubernetes resources were provisioned successfully, but the application is not ready.

This is different from the earlier Greeting example, where successfully reconciling a ConfigMap was effectively enough to consider the Greeting ready.

---

## Readiness Source

Application readiness is derived from:

```text
Deployment.status
```

The Platform Operator currently considers:

```text
Deployment.spec.replicas
Deployment.status.readyReplicas
Deployment.status.availableReplicas
Deployment.status.updatedReplicas
Deployment.status.observedGeneration
```

The flow is:

```text
Application
      |
      v
Deployment
      |
      v
Deployment.status
      |
      v
ApplicationReadinessEvaluator
      |
      v
Application.status
```

---

## ApplicationReadinessEvaluator

Readiness calculation is separated from the reconciler into:

```text
ApplicationReadinessEvaluator.java
```

It produces:

```text
ApplicationReadiness
```

containing:

- `readyReplicas`
- `ready`
- `reason`
- `message`

This keeps reconciliation orchestration separate from the platform-specific readiness rules.

---

## Basic Ready Rule

For the current implementation, an Application is ready when:

```text
Deployment latest generation observed
        AND
updatedReplicas == desiredReplicas
        AND
readyReplicas == desiredReplicas
        AND
availableReplicas == desiredReplicas
```

If all of these are true:

```text
Ready=True
```

Otherwise:

```text
Ready=False
```

This is intentionally a basic readiness model and can evolve later.

---

## Ready Example

Suppose the Deployment reports:

```text
desired replicas   = 3
updated replicas   = 3
ready replicas     = 3
available replicas = 3
```

and the latest Deployment generation has been observed.

The Application can report:

```yaml
readyReplicas: 3

conditions:
  - type: Ready
    status: "True"
    reason: DeploymentReady
    message: Deployment has 3/3 ready replicas
```

---

## Progressing Example

Suppose:

```text
desired replicas = 3
ready replicas   = 1
```

The Application may report:

```yaml
readyReplicas: 1

conditions:
  - type: Ready
    status: "False"
    reason: DeploymentProgressing
    message: Deployment has 1/3 ready replicas
```

The Deployment exists, but the workload is not fully available.

---

## Continuous Readiness

Readiness is not calculated only once during provisioning.

It continuously reflects Deployment state.

Assume the Application is healthy:

```text
Ready=True
3/3 replicas ready
```

Now one Pod becomes unhealthy.

```text
Pod becomes unhealthy
        |
        v
Deployment Controller observes it
        |
        v
Deployment.status changes
        |
        v
Platform Operator receives Deployment update
        |
        v
Application reconciled
        |
        v
readyReplicas decreases
        |
        v
Ready=False
```

Kubernetes then attempts to recover the workload.

When the replacement Pod becomes ready:

```text
replacement Pod ready
        |
        v
Deployment.status changes
        |
        v
Application reconciled
        |
        v
readyReplicas reaches desired count
        |
        v
Ready=True
```

The Application can therefore move through:

```text
Ready=True
3/3
    |
    | workload becomes unhealthy
    v
Ready=False
2/3
    |
    | workload recovers
    v
Ready=True
3/3
```

---

## Who Recovers Failed Pods?

The Platform Operator does not directly create replacement Pods.

That responsibility belongs to the Kubernetes Deployment Controller.

```text
Pod fails
    |
    v
Deployment Controller
    |
    v
replacement Pod
```

The responsibility split is:

```text
Kubernetes Deployment Controller
=
maintain the requested Pods
```

while:

```text
Platform Operator
=
translate Application into Kubernetes resources
and expose workload state through Application.status
```

The Platform Operator observes workload health rather than duplicating Kubernetes workload management.

---

## Why We Do Not Watch Pods Directly Yet

For basic readiness, Deployment status already provides enough information.

```text
Application
     |
     v
Deployment
     |
     v
Deployment.status
     |
     v
Application.status
```

The Platform Operator does not currently inspect individual Pods.

Pod-level observation may become useful later if the platform needs to expose detailed failure reasons such as:

- `CrashLoopBackOff`
- `ImagePullBackOff`
- containers not ready
- scheduling failures
- failed health probes

For now:

```text
Ready
+
readyReplicas
```

is enough.

---

## Application and Deployment Generations

There are two separate generation relationships.

### Application Generation

The Application has:

```text
Application.metadata.generation
```

The Platform Operator reports:

```text
Application.status.observedGeneration
```

For example:

```text
Application generation          = 4
Application observedGeneration  = 4
```

means the Platform Operator has processed the current Application specification.

### Deployment Generation

The Deployment has its own:

```text
Deployment.metadata.generation
```

and:

```text
Deployment.status.observedGeneration
```

For example:

```text
Deployment generation          = 3
Deployment observedGeneration  = 2
```

means the Kubernetes Deployment Controller has not yet processed the latest Deployment specification.

The Application should not be reported as ready for that latest state yet.

---

## Two Reconciliation Layers

There are now two controllers involved.

```text
Application
     |
     v
Platform Operator
     |
     v
Deployment
     |
     v
Kubernetes Deployment Controller
     |
     v
Pods
```

The Platform Operator reconciles:

```text
Application
→ Deployment + Service
```

The Kubernetes Deployment Controller reconciles:

```text
Deployment
→ ReplicaSets + Pods
```

The Platform Operator then observes the Deployment status and projects it back into:

```text
Application.status
```

The complete loop is:

```text
Application desired state
        |
        v
Platform Operator
        |
        v
Deployment desired state
        |
        v
Deployment Controller
        |
        v
Pods
        |
        v
Deployment.status
        |
        v
Platform Operator
        |
        v
Application.status
```

---

## Ready Condition

The Application currently exposes a `Ready` condition.

Current reasons include:

```text
DeploymentPending
DeploymentProgressing
DeploymentReady
```

Example while progressing:

```yaml
conditions:
  - type: Ready
    status: "False"
    reason: DeploymentProgressing
    message: Deployment has 1/3 ready replicas
```

Example when ready:

```yaml
conditions:
  - type: Ready
    status: "True"
    reason: DeploymentReady
    message: Deployment has 3/3 ready replicas
```

---

## lastTransitionTime

The `Ready` condition contains:

```text
lastTransitionTime
```

This represents when the Ready condition actually changed state.

For example:

```text
Ready=False
0/3
```

then:

```text
Ready=False
1/3
```

then:

```text
Ready=False
2/3
```

does not represent a Ready-condition transition.

The condition remains:

```text
False
```

But:

```text
Ready=False
      |
      v
Ready=True
```

is a real transition and receives a new `lastTransitionTime`.

---

## kubectl Output

The Application CRD includes printer columns for workload readiness.

Run:

```bash
kubectl get papp
```

Example when ready:

```text
NAME               READY   READYREPLICAS   IMAGE                    REPLICAS   AGE
greeting-service   True    3               greeting-service:2.0.0   3          5m
```

Example when unhealthy:

```text
NAME               READY   READYREPLICAS   IMAGE                    REPLICAS   AGE
greeting-service   False   2               greeting-service:2.0.0   3          6m
```

This lets developers see application health without inspecting the Deployment directly.

---

## RBAC

The Platform Operator currently needs permissions for:

- Applications
- Application status
- Deployments
- Services

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

Permissions will grow only when new platform features require them.

---

## Running the Operator

Build:

```bash
task platform-operator:build
```

Run Java unit tests:

```bash
task platform-operator:test
```

Build the Docker image:

```bash
task platform-operator:image:build
```

Load it into Kind:

```bash
task platform-operator:image:load
```

Deploy:

```bash
task platform-operator:deploy
```

Restart:

```bash
task platform-operator:restart
```

Check status:

```bash
task platform-operator:status
```

Follow logs:

```bash
task platform-operator:logs
```

---

## Application Commands

Install the CRD:

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

Describe:

```bash
task application:describe
```

Update:

```bash
task application:update
```

Delete:

```bash
task application:delete
```

---

## Testing Strategy

Testing grows alongside the actual platform features.

There are currently three layers.

### API Validation Tests

Run:

```bash
task application:test:api
```

These tests use the real Kubernetes API server with:

```bash
kubectl apply --dry-run=server
```

They verify:

```text
valid Application          → accepted
missing image              → rejected
invalid port               → rejected
invalid replicas           → rejected
```

### Java Unit Tests

Run:

```bash
task platform-operator:test
```

These test platform-specific Java logic without requiring Kubernetes.

The first unit tests cover:

```text
ApplicationReadinessEvaluator
```

Current scenarios include:

```text
all replicas ready
→ Ready=True

some replicas unavailable
→ Ready=False

latest Deployment generation not observed
→ Ready=False
```

### Controller Integration Tests

Run:

```bash
task application:test:controller
```

These use:

```text
real Kind cluster
real Kubernetes API
real Platform Operator
real Application
real Deployment
real Service
```

The integration test currently verifies:

- Application creates Deployment
- Application creates Service
- generated resources match the Application spec
- Application updates propagate
- Deployment remains the same Kubernetes resource
- Service remains the same Kubernetes resource
- Service ClusterIP remains stable
- `observedGeneration` catches up

Readiness integration scenarios can be expanded once the project has a real runnable sample application image.

---

## Current Architecture

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
                 |
                 v
       Kubernetes Deployment
             Controller
                 |
                 v
                Pods
                 |
                 v
         Deployment.status
                 |
                 v
         Platform Operator
                 |
                 v
         Application.status
                 |
        +--------+--------+
        |                 |
        v                 v
 readyReplicas       Ready Condition
```

---

## Project Structure

```text
operators/platform-operator/
├── Dockerfile
├── pom.xml
├── README.md
│
└── src/
    ├── main/
    │   └── java/
    │       └── dev/shubforge/platform/application/
    │           ├── Application.java
    │           ├── ApplicationSpec.java
    │           ├── ApplicationPort.java
    │           ├── ApplicationStatus.java
    │           ├── ApplicationReadiness.java
    │           ├── ApplicationReadinessEvaluator.java
    │           ├── ApplicationReconciler.java
    │           ├── ApplicationDeploymentDependentResource.java
    │           ├── ApplicationServiceDependentResource.java
    │           └── PlatformOperatorApplication.java
    │
    └── test/
        └── java/
            └── dev/shubforge/platform/application/
                └── ApplicationReadinessEvaluatorTest.java
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

## Current Platform Lifecycle

The platform now supports:

```text
Application created
        |
        v
Deployment + Service created
        |
        v
Application updated
        |
        v
existing resources reconciled
        |
        v
Deployment Controller manages Pods
        |
        v
Deployment.status changes
        |
        v
Application readiness updated
```

The Application API now represents both desired configuration and current workload state.

---

## What This Version Proves

The current implementation demonstrates:

- Application API validation
- Application to Deployment translation
- Application to Service translation
- Application updates
- desired-state reconciliation
- Deployment and Service updates in place
- Application `observedGeneration`
- Deployment status observation
- ready replica reporting
- `Ready` condition
- continuous readiness changes
- unit testing of platform readiness rules
- black-box Kubernetes integration testing

---

## What Is Still Missing

The current `Ready` condition tells us whether the Deployment is fully available.

It does not yet explain detailed workload failures such as:

- `ImagePullBackOff`
- `CrashLoopBackOff`
- failed readiness probes
- unschedulable Pods

Those may require deeper workload observation later.

The platform also does not yet support:

- application health probes
- configuration
- Secrets
- resource requests and limits in the Application API
- routes
- authorization
- ApplicationRelease
- API dependencies

---

## What's Next?

The next useful step is to run a real sample workload.

That will allow us to observe an actual lifecycle:

```text
Application created
        |
        v
Ready=False
        |
        v
Deployment creates Pod
        |
        v
Pod becomes Ready
        |
        v
Deployment.status changes
        |
        v
Application Ready=True
```

It will also allow us to test recovery:

```text
Application Ready=True
        |
        v
Pod becomes unhealthy
        |
        v
Application Ready=False
        |
        v
Kubernetes restores workload
        |
        v
Application Ready=True
```

That will give Platform Lab a real end-to-end readiness flow before moving into configuration and Secrets.
