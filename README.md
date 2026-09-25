# Platform Lab

A hands-on learning project for exploring Kubernetes, platform engineering,
operators, cloud-native architecture and developer platforms.

The goal of this project is to learn by building.

## Current Progress

- [x] Repository setup
- [x] SDKMAN Java environment
- [x] Taskfile
- [x] Local Kind cluster
- [ ] Greeting CRD
- [ ] Greeting Operator
- [ ] Reconciliation
- [ ] Operator integration tests
- [ ] GitHub Actions
- [ ] Application API
- [ ] ApplicationRelease
- [ ] API dependencies and access grants

## Prerequisites

- Docker
- SDKMAN
- Java
- kubectl
- Kind
- Task

## Getting Started

Load the project's Java environment:

```bash
sdk env
```

Check required tools:
```bash
task tools:check
```

Create the local Kubernetes cluster:
```bash
task cluster:create
```

Check cluster status:
```bash
task cluster:status
```

Delete the cluster:
```bash
task cluster:delete
```

Project Structure
```text
platform-lab/
├── cluster/
│   └── kind/
├── docs/
├── .sdkmanrc
├── Taskfile.yml
└── README.md
```
