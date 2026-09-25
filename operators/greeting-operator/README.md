# Greeting Operator

The Greeting Operator is the first Kubernetes operator built as part of Platform Lab.

Its purpose is to provide a small example for understanding:

- Custom Resource Definitions
- Kubernetes controllers
- reconciliation
- dependent resources
- operator deployment
- ServiceAccounts
- RBAC
- status
- conditions

The operator is written in Java using the Java Operator SDK.

---

## Overview

The custom resource looks like:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: hello

spec:
  message: "Hello from Platform Lab"
```

The operator watches `Greeting` resources and manages a ConfigMap containing the greeting message.

```text
Greeting
    |
    v
Greeting Operator
    |
    v
ConfigMap
```

For example:

```yaml
spec:
  message: "Hello from Platform Lab"
```

results in:

```yaml
apiVersion: v1
kind: ConfigMap

metadata:
  name: hello-greeting

data:
  message: "Hello from Platform Lab"
```

---

# API

The Greeting API uses:

```text
Group:   platform.shubforge.dev
Version: v1alpha1
Kind:    Greeting
```

The CRD is defined in:

```text
k8s/crds/greetings.yaml
```

The resource is namespace scoped.

---

## Specification

The current specification contains:

```yaml
spec:
  message: string
```

`message` is required.

Example:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: hello

spec:
  message: "Hello from Platform Lab"
```

---

# Controller

The controller is implemented in:

```text
GreetingReconciler.java
```

The ConfigMap is modeled as a managed dependent resource:

```text
GreetingConfigMapDependentResource.java
```

The relationship is:

```text
Greeting
    |
    v
GreetingReconciler
    |
    v
GreetingConfigMapDependentResource
    |
    v
ConfigMap
```

The dependent resource describes the desired ConfigMap.

Conceptually:

```java
protected ConfigMap desired(
        Greeting greeting,
        Context<Greeting> context) {
    ...
}
```

This means the controller focuses on desired state rather than manually implementing separate create and update flows.

---

# Reconciliation

If the Greeting exists:

```text
Greeting
```

the controller expects:

```text
hello-greeting ConfigMap
```

to exist as well.

If the ConfigMap is manually deleted:

```bash
kubectl delete configmap hello-greeting
```

the controller reconciles the cluster and creates it again.

```text
Desired State
     |
     v
Greeting exists
ConfigMap should exist

Actual State
     |
     v
Greeting exists
ConfigMap missing

Controller
     |
     v
Reconcile

Result
     |
     v
ConfigMap recreated
```

---

# Status

The Greeting now exposes operator state through the Kubernetes `status` subresource.

Example:

```yaml
status:
  observedGeneration: 1
  configMapName: hello-greeting

  conditions:
    - type: Ready
      status: "True"
      reason: ConfigMapReady
      message: Managed ConfigMap hello-greeting is in the desired state
```

This allows users to understand the state of the resource without inspecting operator logs.

---

## observedGeneration

Kubernetes tracks changes to the desired resource using:

```yaml
metadata:
  generation: 2
```

The controller reports which generation it has processed:

```yaml
status:
  observedGeneration: 2
```

When these values match:

```text
generation          = 2
observedGeneration  = 2
```

the current status represents the latest desired state processed by the controller.

Conceptually:

```text
User updates spec
      |
      v
generation increases
      |
      v
Operator reconciles
      |
      v
observedGeneration updated
```

---

# Conditions

The operator currently exposes a `Ready` condition.

Example:

```yaml
conditions:

  - type: Ready

    status: "True"

    reason: ConfigMapReady

    message: Managed ConfigMap hello-greeting is in the desired state
```

The condition answers a simple question:

```text
Is the Greeting currently ready?
```

For now:

```text
Ready=True
```

means the managed ConfigMap has been successfully reconciled.

Failure conditions will be introduced later.

---

## lastTransitionTime

Conditions also contain:

```yaml
lastTransitionTime:
```

This represents when the condition changed state.

For example:

```text
Ready=False
     ↓
Ready=True
```

is a condition transition.

Simply running reconciliation again while the condition remains:

```text
Ready=True
```

should not change `lastTransitionTime`.

---

# kubectl Output

The CRD defines additional printer columns.

Instead of only:

```text
NAME    AGE
hello   2m
```

the Greeting resource can expose useful status directly:

```text
NAME    READY   CONFIGMAP        AGE
hello   True    hello-greeting   2m
```

Run:

```bash
kubectl get greetings
```

or:

```bash
kubectl get greet
```

---

# Running the Operator

## Create the cluster

```bash
task cluster:create
```

## Install the CRD

```bash
task crd:install
```

## Build the operator image

```bash
task operator:image:build
```

## Load it into Kind

```bash
task operator:image:load
```

## Deploy the operator

```bash
task operator:deploy
```

## Check the operator

```bash
task operator:status
```

## Follow logs

```bash
task operator:logs
```

---

# Create a Greeting

Create the sample resource:

```bash
task greeting:create
```

Check it:

```bash
task greeting:get
```

Check complete status:

```bash
task greeting:status
```

or:

```bash
kubectl get greeting hello -o yaml
```

---

# Operator Deployment

The operator runs in:

```text
platform-system
```

as a Kubernetes Deployment.

```text
Deployment
    |
    v
Pod
    |
    v
ServiceAccount
```

The Pod uses:

```text
platform-system/greeting-operator
```

as its ServiceAccount.

---

# RBAC

The operator needs permissions to:

```text
get/list/watch Greetings
update Greeting status

get/list/watch ConfigMaps
create/update/delete ConfigMaps
```

The current setup uses:

```text
ClusterRole
+
ClusterRoleBinding
```

which allows the operator to work across namespaces.

The authorization relationship is:

```text
Operator Pod
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

The ServiceAccount lives in:

```text
platform-system
```

but its ClusterRoleBinding allows access to matching resources in other namespaces.

---

# Verify RBAC

Check whether the operator can list Greetings:

```bash
kubectl auth can-i \
  list greetings.platform.shubforge.dev \
  --all-namespaces \
  --as=system:serviceaccount:platform-system:greeting-operator
```

Expected:

```text
yes
```

Check ConfigMap creation:

```bash
kubectl auth can-i \
  create configmaps \
  --all-namespaces \
  --as=system:serviceaccount:platform-system:greeting-operator
```

Expected:

```text
yes
```

---

# Testing Across Namespaces

Create another namespace:

```bash
kubectl create namespace demo
```

Create a Greeting in that namespace:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: hello
  namespace: demo

spec:
  message: "Hello from demo"
```

Check:

```bash
kubectl get greetings -n demo
```

and:

```bash
kubectl get configmaps -n demo
```

The operator remains in:

```text
platform-system
```

while reconciling resources in:

```text
demo
```

---

# Project Files

```text
operators/greeting-operator/
├── Dockerfile
├── pom.xml
├── README.md
│
└── src/
    └── main/
        └── java/
            └── dev/shubforge/platform/greeting/
                ├── Greeting.java
                ├── GreetingSpec.java
                ├── GreetingStatus.java
                ├── GreetingReconciler.java
                ├── GreetingConfigMapDependentResource.java
                └── GreetingOperatorApplication.java
```

The related Kubernetes resources are located under:

```text
k8s/
├── crds/
│   └── greetings.yaml
│
├── operator/
│   ├── namespace.yaml
│   ├── service-account.yaml
│   ├── rbac.yaml
│   └── deployment.yaml
│
└── samples/
    └── greeting.yaml
```

---

# Current Flow

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
Greeting Operator
      |
      v
ConfigMap
      |
      v
Greeting Status
```

The operator now manages both:

```text
desired Kubernetes resources
```

and:

```text
observable status
```

---

# What's Next?

The next operator improvements will focus on failure scenarios.

For example:

```yaml
conditions:

  - type: Ready
    status: "False"
    reason: ReconciliationFailed
    message: ...
```

Future areas include:

- failure conditions
- retry behavior
- Kubernetes Events
- finalizers
- error handling
- integration tests
- GitHub Actions
