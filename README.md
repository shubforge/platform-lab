# Platform Lab

A hands-on learning project for exploring Kubernetes, platform engineering,
operators, cloud-native architecture and developer platforms.

The goal of this project is to learn by building.

## Current Progress

- [x] Repository setup
- [x] SDKMAN Java environment
- [x] Taskfile
- [x] Local Kind cluster
- [x] Greeting CRD
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

You can verify the required tools using:

```bash
task tools:check
```

## Java Setup

The Java version used by the project is defined in:

```text
.sdkmanrc
```

Load the project Java environment using:

```bash
sdk env
```

If the required Java version is not installed:

```bash
sdk env install
```

## Local Kubernetes Cluster

The project uses [Kind](https://kind.sigs.k8s.io/) for running a local Kubernetes cluster.

Create the cluster:

```bash
task cluster:create
```

Check the cluster:

```bash
task cluster:status
```

You can also verify the current Kubernetes context:

```bash
kubectl config current-context
```

The expected context is:

```text
kind-platform-lab
```

Delete the local cluster:

```bash
task cluster:delete
```

## Project Structure

```text
platform-lab/
├── .github/
│   └── workflows/
│
├── cluster/
│   └── kind/
│       └── cluster.yaml
│
├── docs/
│
├── k8s/
│   ├── crds/
│   │   └── greetings.yaml
│   │
│   └── samples/
│       └── greeting.yaml
│
├── .editorconfig
├── .gitignore
├── .sdkmanrc
├── README.md
└── Taskfile.yml
```

The structure will continue to evolve as new parts of the platform are added.

---

# Greeting Custom Resource

The first Kubernetes feature added to Platform Lab is a custom resource called `Greeting`.

The purpose of this feature is to understand how Kubernetes can be extended with our own APIs before building a controller or operator.

## Custom Resource Definition

Kubernetes already understands resources such as:

```text
Pod
Service
Deployment
ConfigMap
Secret
```

But Kubernetes does not initially know what a resource such as this means:

```yaml
apiVersion: platform.example.dev/v1alpha1
kind: Greeting

metadata:
  name: hello

spec:
  message: "Hello from Platform Lab"
```

To introduce our own resource type, we create a Kubernetes `CustomResourceDefinition`.

The Greeting CRD is available at:

```text
k8s/crds/greetings.yaml
```

The CRD defines a new Kubernetes API:

```text
Greeting
```

with the API version:

```text
platform.example.dev/v1alpha1
```

The first version is named `v1alpha1` because the API is still experimental and will evolve as the project grows.

## Greeting Schema

For now, the `Greeting` resource contains only one field:

```yaml
spec:
  message: "Hello from Platform Lab"
```

The CRD defines `message` as a required string.

This means Kubernetes can validate the custom resource before storing it.

For example, this is valid:

```yaml
apiVersion: platform.example.dev/v1alpha1
kind: Greeting

metadata:
  name: hello

spec:
  message: "Hello from Platform Lab"
```

A Greeting without a `message` will be rejected because the field is required by the CRD schema.

## Install the Greeting CRD

Make sure the local Kubernetes cluster is running:

```bash
task cluster:create
```

Install the CRD:

```bash
task crd:install
```

Verify the installed CRD:

```bash
kubectl get crds
```

You should see the Greeting CRD:

```text
greetings.platform.example.dev
```

You can also inspect it:

```bash
kubectl describe crd greetings.platform.example.dev
```

## Create a Greeting

A sample Greeting resource is available at:

```text
k8s/samples/greeting.yaml
```

Create it using:

```bash
task greeting:create
```

The resource should now be stored by Kubernetes.

List all Greeting resources:

```bash
task greeting:get
```

Example:

```text
NAME    AGE
hello   10s
```

You can inspect the Greeting using:

```bash
task greeting:describe
```

Or directly with kubectl:

```bash
kubectl get greeting hello -o yaml
```

## What Changed in Kubernetes?

Before installing the CRD, running:

```bash
kubectl get greetings
```

would fail because Kubernetes did not know about a resource called `Greeting`.

After installing the CRD:

```text
Greeting CRD
      |
      v
Kubernetes API
      |
      v
Greeting becomes a valid Kubernetes resource
```

Kubernetes can now:

- validate a Greeting
- store a Greeting
- retrieve a Greeting
- update a Greeting
- delete a Greeting
- allow controllers to watch Greeting resources

For example:

```bash
kubectl get greetings
```

works just like commands for built-in Kubernetes resources.

## Does the Greeting Do Anything Yet?

Not yet.

At this stage:

```text
Greeting
   |
   v
Kubernetes API
   |
   v
Stored
```

Kubernetes understands and stores the resource, but nothing reacts to it.

Creating:

```yaml
spec:
  message: "Hello from Platform Lab"
```

does not create a ConfigMap, Pod, Deployment, or any other resource.

For that, we need a controller.

The controller will watch `Greeting` resources and react whenever they are created, updated, or deleted.

The next step of the project will look something like:

```text
Greeting
    |
    v
Greeting Controller
    |
    v
ConfigMap
```

This will also introduce one of the most important concepts behind Kubernetes controllers: **reconciliation**.

---

# Available Tasks

The project uses Task to keep common development commands in one place.

List all available tasks:

```bash
task
```

or:

```bash
task --list
```

### Development Tools

Check locally installed tools:

```bash
task tools:check
```

### Cluster

Create the cluster:

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

### Custom Resources

Install CRDs:

```bash
task crd:install
```

Delete CRDs:

```bash
task crd:delete
```

### Greeting

Create the sample Greeting:

```bash
task greeting:create
```

List Greetings:

```bash
task greeting:get
```

Describe the sample Greeting:

```bash
task greeting:describe
```

Delete the sample Greeting:

```bash
task greeting:delete
```

---

# What's Next?

The next step is to build the first Kubernetes controller.

The Greeting controller will:

1. Watch `Greeting` resources.
2. Read the message from the Greeting specification.
3. Create a ConfigMap containing that message.
4. Keep the ConfigMap synchronized with the Greeting.
5. Recreate the ConfigMap if it is manually deleted.

This will help explore how Kubernetes controllers continuously reconcile the desired state with the actual state.

The project will gradually expand from this simple example toward higher-level platform APIs and abstractions.
