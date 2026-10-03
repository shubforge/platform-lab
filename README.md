# Platform Lab

Platform Lab is a learning project for exploring how a developer platform can be built on top of Kubernetes.

The goal is to create simple developer-facing APIs while the platform handles lower-level Kubernetes infrastructure.

Instead of requiring developers to manage resources such as:

```text
Deployments
Services
ConfigMaps
Secrets
ServiceAccounts
network policies
authorization policies
gateway configuration
```

the platform should provide higher-level APIs.

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

The platform operator will eventually translate that into the required Kubernetes resources.

---

## Project Status

The project started with a small `Greeting` operator used to learn Kubernetes controller fundamentals.

That work currently covers:

- Custom Resource Definitions
- Java Operator SDK
- reconciliation
- managed dependent resources
- status and conditions
- RBAC
- failure handling
- retries
- Kubernetes Events
- manual reconciliation
- event filtering
- owner references
- Kubernetes garbage collection

The project has now moved into building the actual platform APIs.

Current platform work:

```text
Application API        ✓
Application validation ✓
Application API tests  ✓

Application controller       next
Deployment management        next
Service management           next
Application status           next
```

---

# Architecture

The long-term direction is:

```text
                   Developer
                       |
                       v
                Platform APIs
                       |
                       v
                Platform Operator
                       |
       +---------------+---------------+
       |               |               |
       v               v               v
   Workloads        Networking      Security
       |               |               |
       v               v               v
  Deployment         Service       Authorization
  ConfigMap          Routes        Identity
  Secrets            Gateway       Access
```

The platform APIs should hide unnecessary Kubernetes implementation details from application developers.

---

# Application API

The first real platform API is:

```text
Application
```

Example:

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

The initial API intentionally stays small.

It currently supports:

```text
image
replicas
containerPort
```

Future capabilities will be added only when the platform implements them.

---

## Application API Flow

Currently:

```text
Application YAML
      |
      v
Kubernetes API
      |
      v
Application Custom Resource
```

The next stage will become:

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

---

# Application Validation

The CRD performs basic API validation.

Examples include:

```text
image must be provided

replicas must be >= 1

containerPort must be between
1 and 65535
```

Invalid configuration is rejected by Kubernetes before it reaches the controller.

---

# API Testing

Platform API tests are located under:

```text
scripts/tests/
```

The Application API test is:

```text
scripts/tests/application-api-test.sh
```

It uses:

```bash
kubectl apply --dry-run=server
```

to test the CRD against the real Kubernetes API server without persisting test resources.

The current tests verify:

```text
valid Application          → accepted

missing image              → rejected

invalid container port     → rejected

replicas below minimum     → rejected
```

Run:

```bash
task application:test:api
```

Testing will grow alongside each platform capability.

When the Java platform operator is introduced, controller integration tests will be added as part of the same feature development.

---

# Greeting Operator

The Greeting Operator remains in the project as a small reference implementation for Kubernetes operator concepts.

```text
Greeting
    |
    v
Greeting Operator
    |
    v
ConfigMap
```

Its detailed documentation is available at:

```text
operators/greeting-operator/README.md
```

The Greeting operator is primarily a learning/reference component rather than the long-term platform API.

---

# Repository Structure

```text
platform-lab/
│
├── cluster/
│   └── kind/
│       └── cluster.yaml
│
├── docs/
│
├── k8s/
│   ├── crds/
│   │   ├── greetings.yaml
│   │   └── applications.yaml
│   │
│   ├── operator/
│   │
│   └── samples/
│       ├── greeting.yaml
│       └── application.yaml
│
├── operators/
│   └── greeting-operator/
│       ├── Dockerfile
│       ├── pom.xml
│       ├── README.md
│       └── src/
│
├── scripts/
│   └── tests/
│       └── application-api-test.sh
│
├── .sdkmanrc
├── README.md
└── Taskfile.yml
```

This structure will evolve as the real platform operator is introduced.

---

# Prerequisites

The local development environment currently uses:

```text
Java
Maven
Docker
kubectl
Kind
Task
```

Java configuration is managed through:

```text
.sdkmanrc
```

---

# Local Kubernetes Cluster

Create the Kind cluster:

```bash
task cluster:create
```

Check it:

```bash
task cluster:status
```

Delete it:

```bash
task cluster:delete
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

Describe:

```bash
task application:describe
```

Delete:

```bash
task application:delete
```

Run Application API tests:

```bash
task application:test:api
```

---

# Development Approach

Platform Lab is built incrementally.

Each feature should ideally include:

```text
API
+
implementation
+
tests
+
documentation
```

The goal is not to introduce every Kubernetes or platform concept immediately.

Capabilities are added when an actual platform requirement needs them.

---

# Roadmap

The current direction is:

```text
Greeting Operator
      ✓
      |
      v
Application API
      ✓
      |
      v
Application Controller
      |
      +---- Deployment
      |
      +---- Service
      |
      v
Application Status
      |
      v
Configuration
      |
      v
Secrets
      |
      v
ApplicationRelease
      |
      v
ApiDependency
      |
      v
AccessGrant
      |
      v
Networking / Authorization
```

Additional concepts such as finalizers, external API reconciliation, periodic reconciliation, and advanced lifecycle handling will be introduced when a real platform use case requires them.

---

# Learning in Public

This repository is also the code behind my **Platform Lab: Building on Kubernetes** blog series.

The goal is to build the platform incrementally while documenting what I learn along the way.

The project started with Kubernetes operator fundamentals and is now moving into real platform API design and implementation.
