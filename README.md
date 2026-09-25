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
- [x] Java Greeting Controller
- [x] ConfigMap creation from Greeting
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

---

## Maven

The project uses Maven Wrapper from the repository root:

```text
platform-lab/
├── .mvn/
├── mvnw
├── mvnw.cmd
└── ...
```

The Greeting operator has its own Maven project:

```text
operators/greeting-operator/pom.xml
```

Because the Maven Wrapper is maintained at the repository root, the operator can be built directly using:

```bash
./mvnw -f operators/greeting-operator/pom.xml clean package
```

Normally this does not need to be run manually because the Taskfile provides simpler commands such as:

```bash
task operator:build
```

---

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
├── .mvn/
│   └── wrapper/
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
├── operators/
│   └── greeting-operator/
│       ├── pom.xml
│       └── src/
│           └── main/
│               └── java/
│                   └── dev/
│                       └── shubforge/
│                           └── platform/
│                               └── greeting/
│                                   ├── Greeting.java
│                                   ├── GreetingSpec.java
│                                   ├── GreetingStatus.java
│                                   ├── GreetingReconciler.java
│                                   ├── GreetingConfigMapDependentResource.java
│                                   └── GreetingOperatorApplication.java
│
├── .editorconfig
├── .gitignore
├── .sdkmanrc
├── mvnw
├── mvnw.cmd
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
apiVersion: platform.shubforge.dev/v1alpha1
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
platform.shubforge.dev/v1alpha1
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
apiVersion: platform.shubforge.dev/v1alpha1
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
greetings.platform.shubforge.dev
```

You can also inspect it:

```bash
kubectl describe crd greetings.platform.shubforge.dev
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

# Greeting Controller

Creating a CRD gives Kubernetes a new API, but it does not add any behavior.

Without a controller, the flow is simply:

```text
Greeting
    |
    v
Kubernetes API
    |
    v
Stored
```

The next step is therefore to build something that reacts to a `Greeting`.

For the first controller, Platform Lab uses Java and the Java Operator SDK.

The controller watches `Greeting` resources and manages a ConfigMap for each Greeting.

The flow now becomes:

```text
Greeting
    |
    v
Greeting Controller
    |
    v
ConfigMap
```

For example:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: hello

spec:
  message: "Hello from Platform Lab"
```

results in a ConfigMap similar to:

```yaml
apiVersion: v1
kind: ConfigMap

metadata:
  name: hello-greeting

data:
  message: "Hello from Platform Lab"
```

---

## Greeting Java Model

The Kubernetes custom resource is represented in Java by the `Greeting` class.

Conceptually:

```java
@Group("platform.shubforge.dev")
@Version("v1alpha1")
@Kind("Greeting")
@Plural("greetings")
public class Greeting
        extends CustomResource<GreetingSpec, GreetingStatus>
        implements Namespaced {
}
```

`GreetingSpec` represents the `spec` section of the Kubernetes resource.

For now it contains only:

```java
private String message;
```

`GreetingStatus` is currently empty and will be expanded later when controller status handling is introduced.

---

## Greeting Controller

The controller is implemented by:

```text
GreetingReconciler.java
```

It watches `Greeting` resources.

The desired ConfigMap is defined by:

```text
GreetingConfigMapDependentResource.java
```

Instead of manually writing logic such as:

```text
if ConfigMap does not exist
    create it

if ConfigMap changed
    update it
```

the controller describes the desired ConfigMap.

The operator framework then works toward keeping the actual Kubernetes state aligned with that desired state.

This introduces the foundation for Kubernetes reconciliation.

---

## Build the Controller

Build the Java operator using:

```bash
task operator:build
```

Internally, the Taskfile runs:

```bash
./mvnw \
  -f operators/greeting-operator/pom.xml \
  clean package
```

Keeping this command inside the Taskfile means developers do not need to remember where the operator `pom.xml` is located.

---

## Run the Controller Locally

For now, the controller runs as a Java process outside Kubernetes.

Start it using:

```bash
task operator:run
```

The current architecture looks like:

```text
Mac
 |
 | Java Process
 |
 | Greeting Controller
 |
 | kubeconfig
 v
Kind Kubernetes Cluster
```

The controller uses the local Kubernetes configuration to connect to the same cluster used by `kubectl`.

The operator is not yet deployed inside Kubernetes.

That will be added later with:

```text
Docker Image
     |
     v
Kubernetes Deployment
     |
     v
ServiceAccount
     |
     v
RBAC
```

---

# Running the Complete Example

Start the local cluster:

```bash
task cluster:create
```

Install the CRD:

```bash
task crd:install
```

Build the Java controller:

```bash
task operator:build
```

Run the controller:

```bash
task operator:run
```

Keep this terminal running.

Open another terminal and create a Greeting:

```bash
task greeting:create
```

Check the Greeting:

```bash
task greeting:get
```

Now check the generated ConfigMap:

```bash
kubectl get configmaps
```

You should see something similar to:

```text
NAME             DATA   AGE
hello-greeting   1      10s
```

Inspect it:

```bash
kubectl get configmap hello-greeting -o yaml
```

The ConfigMap should contain:

```yaml
data:
  message: Hello from Platform Lab
```

The complete flow is now:

```text
Greeting YAML
     |
     v
Kubernetes API
     |
     v
Greeting Resource
     |
     v
Greeting Controller
     |
     v
Desired ConfigMap
     |
     v
hello-greeting
```

---

# Testing Reconciliation

One interesting experiment is to manually delete the generated ConfigMap:

```bash
kubectl delete configmap hello-greeting
```

Then check again:

```bash
kubectl get configmaps
```

The controller should reconcile the state and recreate the ConfigMap.

Another experiment is to update the Greeting:

```yaml
spec:
  message: "Hello from the updated Greeting"
```

Apply it again:

```bash
task greeting:create
```

Then inspect the ConfigMap:

```bash
kubectl get configmap hello-greeting -o yaml
```

The ConfigMap should move toward the new desired state.

These scenarios will be explored further as reconciliation support is developed and tested.

---
# Taskfile

The project uses a root `Taskfile.yml` to provide a common developer interface.

Instead of remembering individual Kind, kubectl, and Maven commands, common operations can be run using `task`.

List all available tasks:

```bash
task --list
```

## Tooling

Check required tools:

```bash
task tools:check
```

## Cluster

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

## CRDs

Install CRDs:

```bash
task crd:install
```

Delete CRDs:

```bash
task crd:delete
```

## Greeting

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

## Operator

Build the Greeting operator:

```bash
task operator:build
```

Run the Greeting operator locally:

```bash
task operator:run
```

---

# What We Have So Far

The project started with:

```text
CustomResourceDefinition
        |
        v
Greeting
```

It has now evolved into:

```text
CustomResourceDefinition
        |
        v
Greeting
        |
        v
Java Controller
        |
        v
ConfigMap
```

This is the first complete controller flow in Platform Lab.

---

# What's Next?

The next steps are to explore the controller lifecycle in more detail.

This includes:

- updating managed resources when a Greeting changes
- recreating resources when they are deleted
- understanding owner references
- understanding reconciliation
- adding automated tests
- packaging the operator as a Docker image
- adding Kubernetes RBAC
- running the operator inside the Kubernetes cluster

After that, the same ideas can gradually be applied to more useful platform APIs.

The simple `Greeting` resource is only the starting point.
