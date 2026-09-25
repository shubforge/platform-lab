# Platform Lab

Platform Lab is a hands-on project for learning Kubernetes, operators, platform engineering, and developer platform concepts by building them step by step.

The project starts with a small custom Kubernetes API and gradually evolves toward higher-level platform abstractions.

## Current Architecture

```text
Custom Resource
      |
      v
Kubernetes API
      |
      v
Java Operator
      |
      v
Managed Kubernetes Resources
```

The first implementation is a simple `Greeting` operator.

```text
Greeting
    |
    v
Greeting Operator
    |
    v
ConfigMap
```

## Current Progress

- [x] Local Kind cluster
- [x] Taskfile based developer workflow
- [x] Greeting CRD
- [x] Java Greeting controller
- [x] ConfigMap reconciliation
- [x] Operator Docker image
- [x] Operator deployment inside Kubernetes
- [x] ServiceAccount and RBAC
- [x] Cluster-wide Greeting reconciliation
- [x] Greeting status and conditions
- [ ] Failure status and retries
- [ ] Kubernetes Events
- [ ] Finalizers
- [ ] Integration tests
- [ ] GitHub Actions
- [ ] Application API
- [ ] ApplicationRelease
- [ ] ApiDependency
- [ ] AccessGrant

## Prerequisites

- Docker
- SDKMAN
- Java
- kubectl
- Kind
- Task

Check the local environment:

```bash
task tools:check
```

## Getting Started

Load the project Java version:

```bash
sdk env
```

Create the local Kubernetes cluster:

```bash
task cluster:create
```

Install the CRDs:

```bash
task crd:install
```

Build the Greeting operator image:

```bash
task operator:image:build
```

Load the image into Kind:

```bash
task operator:image:load
```

Deploy the operator:

```bash
task operator:deploy
```

Create the sample Greeting:

```bash
task greeting:create
```

Check it:

```bash
kubectl get greetings
```

## Components

### Greeting Operator

The first operator in Platform Lab.

It introduces:

- Custom Resource Definitions
- Java Operator SDK
- reconciliation
- dependent resources
- ConfigMap management
- Docker packaging
- ServiceAccounts
- Kubernetes RBAC
- operator deployment
- status and conditions

See:

[`operators/greeting-operator/README.md`](operators/greeting-operator/README.md)

## Project Structure

```text
platform-lab/
├── cluster/
│   └── kind/
│
├── docs/
│
├── k8s/
│   ├── crds/
│   ├── operator/
│   └── samples/
│
├── operators/
│   └── greeting-operator/
│
├── Taskfile.yml
├── .sdkmanrc
└── README.md
```

## Development Commands

List all available commands:

```bash
task --list
```

The Taskfile is the main developer interface for the repository.

## What's Next?

The project will gradually move from the simple Greeting example toward higher-level platform APIs such as:

```text
Application
ApplicationRelease
ApiDependency
AccessGrant
```

The goal is to understand the Kubernetes building blocks first and then use them to build platform abstractions.
