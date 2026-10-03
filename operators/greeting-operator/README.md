# Greeting Operator

The Greeting Operator is the first Kubernetes operator built as part of Platform Lab.

It is a small learning project used to understand Kubernetes operator concepts step by step.

The operator currently covers:

- Custom Resource Definitions
- Java Operator SDK
- controllers and dependent resources
- reconciliation
- status and conditions
- ServiceAccounts and RBAC
- failure handling and retries
- Kubernetes Events
- manual reconciliation
- event filtering
- owner references
- Kubernetes garbage collection
- resource deletion behavior

The operator is written in Java using the Java Operator SDK.

---

## Overview

A `Greeting` looks like:

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

The operator also reports its current state through:

```text
Greeting.status
```

---

# API

The custom API uses:

```text
Group:   platform.shubforge.dev
Version: v1alpha1
Kind:    Greeting
```

The CRD is defined in:

```text
k8s/crds/greetings.yaml
```

`Greeting` is a namespaced resource.

---

# Specification

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

The primary controller is implemented in:

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

The controller describes desired state instead of manually implementing separate create and update flows.

---

# Status

The `Greeting` resource exposes operator state using the Kubernetes `status` subresource.

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

This allows users to inspect the current state without checking operator logs.

---

## Spec vs Status

A useful mental model is:

```text
spec
=
what the user wants
```

and:

```text
status
=
what the operator observed or achieved
```

So:

```text
User
 |
 v
spec
 |
 v
Operator
 |
 v
status
```

---

# observedGeneration

Kubernetes tracks changes to desired configuration using:

```yaml
metadata:
  generation:
```

The operator reports which generation it has processed using:

```yaml
status:
  observedGeneration:
```

For example:

```text
generation          = 2
observedGeneration  = 2
```

means the operator has processed the current desired state.

---

# Conditions

The operator currently exposes a `Ready` condition.

Successful reconciliation:

```yaml
conditions:
  - type: Ready
    status: "True"
    reason: ConfigMapReady
    message: Managed ConfigMap hello-greeting is in the desired state
```

Failed reconciliation:

```yaml
conditions:
  - type: Ready
    status: "False"
    reason: ReconciliationFailed
    message: ...
```

So the resource can move between:

```text
Ready=True
```

and:

```text
Ready=False
```

depending on the reconciliation result.

---

# Failure Handling

A failed reconciliation updates status, records a Kubernetes Event, and allows the retry mechanism to run.

```text
Greeting
    |
    v
Operator
    |
    v
Failure
    |
    +----> Ready=False
    |
    +----> Warning Event
    |
    +----> Retry
```

The full exception remains available in operator logs.

The resource status contains a shorter user-facing failure message.

---

# Retry Behavior

Failed reconciliations are retried automatically.

```text
reconcile
    |
    v
failure
    |
    v
retry
    |
    v
failure
    |
    v
retry
```

Retries are limited.

Once retries are exhausted, another relevant event is required before the resource is reconciled again.

This helped separate two concepts:

```text
Retry
=
another attempt after a failed reconciliation
```

and:

```text
Reconciliation trigger
=
a new event that causes the controller
to evaluate the resource again
```

---

# Kubernetes Events

The operator records Kubernetes Events for important outcomes.

Successful reconciliation:

```text
Normal  Reconciled
```

Failed reconciliation:

```text
Warning  ReconciliationFailed
```

Inspect them using:

```bash
kubectl describe greeting hello
```

Example:

```text
Events:
  Type    Reason      Age                 From                Message
  ----    ------      ----                ----                -------
  Normal  Reconciled  5s (x2 over 2m12s) greetingreconciler  Managed ConfigMap hello-greeting is in the desired state
```

Repeated equivalent Events may be aggregated by Kubernetes.

---

# Manual Reconciliation

The operator supports an explicit manual reconciliation annotation:

```text
platform.shubforge.dev/reconcile-at
```

Example:

```bash
kubectl annotate greeting hello \
  platform.shubforge.dev/reconcile-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --overwrite
```

This requests another reconciliation without changing the desired `spec`.

The Taskfile provides:

```bash
task greeting:reconcile
```

For another Greeting:

```bash
task greeting:reconcile GREETING_NAME=<name>
```

---

# Generation vs Manual Reconciliation

A manual reconciliation only changes metadata.

It does not change:

```yaml
spec:
```

so the Kubernetes generation remains unchanged.

For example:

```text
generation          = 1
observedGeneration  = 1
```

can remain exactly the same even after several manual reconciliations.

The distinction is:

```text
generation
=
version of the desired specification
```

while:

```text
reconcile-at
=
request to evaluate the current specification again
```

---

# Update Filtering

The controller uses:

```text
GreetingUpdateFilter.java
```

to decide which primary resource updates should trigger reconciliation.

The controller reconciles when:

```text
Greeting.spec changes
```

or when:

```text
platform.shubforge.dev/reconcile-at changes
```

Conceptually:

```java
return generationChanged(newResource, oldResource)
        || reconcileAnnotationChanged(
                newResource,
                oldResource
        );
```

Updates caused only by status changes are ignored by this filter.

This helps avoid unnecessary reconciliation loops.

---

# Resource Ownership

The ConfigMap created by the operator is a dependent resource of the `Greeting`.

The lifecycle relationship is:

```text
Greeting
   |
   | owns
   v
ConfigMap
```

Inspect the generated ConfigMap:

```bash
kubectl get configmap hello-greeting -o yaml
```

The current operator produces metadata like:

```yaml
metadata:
  labels:
    app.kubernetes.io/managed-by: greeting-operator

  ownerReferences:
    - apiVersion: platform.shubforge.dev/v1alpha1
      kind: Greeting
      name: hello
      uid: ...
      controller: false
      blockOwnerDeletion: false
```

The important part is:

```text
ownerReferences
```

This creates an ownership relationship between:

```text
Greeting/hello
```

and:

```text
ConfigMap/hello-greeting
```

---

# Understanding the Owner Reference

The ConfigMap contains:

```yaml
ownerReferences:
  - apiVersion: platform.shubforge.dev/v1alpha1
    kind: Greeting
    name: hello
    uid: ...
```

The `uid` is particularly important.

Kubernetes does not identify the owner only by:

```text
kind + name
```

It also tracks the unique identity of that specific resource.

So the relationship is effectively:

```text
ConfigMap
    |
    | owned by
    v
Greeting UID
```

This prevents a newly created resource with the same name from automatically becoming the owner of an old dependent.

---

# controller: false

The current owner reference contains:

```yaml
controller: false
```

This does not mean the ConfigMap has no owner.

The ownership relationship still exists.

The `controller` field has a more specific meaning: it identifies an owner reference as the managing controller reference.

The current dependent resource uses a normal owner reference:

```text
Greeting
    |
    | ownerReference
    v
ConfigMap
```

For the lifecycle behavior being explored here, the important part is that the owner reference exists.

---

# blockOwnerDeletion: false

The current owner reference also contains:

```yaml
blockOwnerDeletion: false
```

This controls whether this dependent should block deletion of the owner during foreground deletion.

For the simple Greeting and ConfigMap relationship, the ConfigMap does not need to prevent the Greeting from being deleted.

The relationship can still be used by Kubernetes garbage collection.

---

# Owner and Dependent Resources

For the current operator:

```text
Owner
=
Greeting
```

and:

```text
Dependent
=
ConfigMap
```

The ownership information is stored on the dependent:

```text
Greeting
    |
    | ownerReference
    v
ConfigMap
```

This allows Kubernetes to understand that the two resource lifecycles are related.

---

# Deleting the Managed ConfigMap

First create the Greeting:

```bash
task greeting:create
```

Verify both resources:

```bash
kubectl get greeting hello
```

and:

```bash
kubectl get configmap hello-greeting
```

Now delete only the ConfigMap:

```bash
kubectl delete configmap hello-greeting
```

The Greeting still exists.

The desired state therefore still says:

```text
Greeting exists
        |
        v
ConfigMap should exist
```

The dependent resource event causes the controller to reconcile and recreate the ConfigMap.

Check again:

```bash
kubectl get configmap hello-greeting
```

The ConfigMap should exist again.

The lifecycle is:

```text
ConfigMap deleted
       |
       v
Greeting still exists
       |
       v
desired state still requires ConfigMap
       |
       v
operator reconciles
       |
       v
ConfigMap recreated
```

---

# Deleting the Greeting

Now test the opposite direction.

Start with:

```text
Greeting/hello
ConfigMap/hello-greeting
```

You can inspect both:

```bash
kubectl get greeting hello
kubectl get configmap hello-greeting
```

Then delete only the owner:

```bash
kubectl delete greeting hello
```

Check:

```bash
kubectl get greeting hello
```

Then:

```bash
kubectl get configmap hello-greeting
```

Once Kubernetes garbage collection completes, the ConfigMap should no longer exist.

The flow is:

```text
Greeting deleted
      |
      v
owner reference becomes invalid
      |
      v
Kubernetes garbage collector
      |
      v
dependent ConfigMap deleted
```

The controller does not need to manually execute:

```java
deleteConfigMap();
```

for this Kubernetes-owned dependent.

---

# Watching Garbage Collection

One useful way to observe the lifecycle is to watch the ConfigMap.

In one terminal:

```bash
kubectl get configmap hello-greeting -w
```

In another terminal:

```bash
kubectl delete greeting hello
```

This makes it easier to see when the dependent disappears.

You can also watch both resources:

```bash
kubectl get greeting,configmap -w
```

---

# Two Different Delete Scenarios

These two experiments look similar but behave differently.

## Delete the dependent

```bash
kubectl delete configmap hello-greeting
```

The owner still exists:

```text
Greeting
   |
   v
still present
```

The desired state still requires the ConfigMap.

So:

```text
ConfigMap deleted
        |
        v
operator observes change
        |
        v
reconcile
        |
        v
ConfigMap recreated
```

---

## Delete the owner

```bash
kubectl delete greeting hello
```

Now the source of the desired state disappears.

So:

```text
Greeting deleted
        |
        v
owner reference
        |
        v
Kubernetes garbage collection
        |
        v
ConfigMap removed
```

The operator should not keep recreating the ConfigMap because the `Greeting` itself no longer exists.

---

# Kubernetes Garbage Collection

Owner references allow Kubernetes garbage collection to clean up dependent Kubernetes resources.

For this operator:

```text
Greeting
    |
    v
ConfigMap
```

When the owner is removed:

```text
delete Greeting
      |
      v
Kubernetes finds dependent
      |
      v
delete ConfigMap
```

This gives us Kubernetes-native lifecycle management without writing explicit cleanup code for every dependent Kubernetes object.

---

# Reconciliation vs Garbage Collection

This experiment demonstrates two different mechanisms.

When the dependent is deleted:

```text
ConfigMap deleted
      |
      v
reconciliation
      |
      v
ConfigMap recreated
```

When the owner is deleted:

```text
Greeting deleted
      |
      v
garbage collection
      |
      v
ConfigMap removed
```

So:

```text
Reconciliation
=
maintain desired state while the owner exists
```

while:

```text
Garbage collection
=
remove dependents when their owner disappears
```

These mechanisms work together.

---

# Namespaces and Ownership

`Greeting` is namespaced.

The managed ConfigMap is created in the same namespace.

For example:

```text
default/hello
      |
      v
default/hello-greeting
```

and:

```text
demo/hello
      |
      v
demo/hello-greeting
```

The dependent resource uses the Greeting namespace when building the ConfigMap.

Conceptually:

```java
var namespace =
        greeting.getMetadata().getNamespace();
```

and:

```java
.withNamespace(namespace)
```

This keeps the owner and namespaced dependent together.

---

# Owner References vs Finalizers

Owner references and finalizers solve different lifecycle problems.

## Owner reference

An owner reference says:

```text
This Kubernetes resource belongs to another Kubernetes resource.
```

For example:

```text
Greeting
    |
    v
ConfigMap
```

Kubernetes garbage collection can clean up the ConfigMap when the Greeting is removed.

---

## Finalizer

A finalizer says:

```text
Do not completely remove this resource yet.
The controller still has cleanup work to do.
```

This becomes useful when cleanup cannot be handled automatically by Kubernetes.

For example:

```text
Custom Resource
      |
      v
External API
      |
      v
External Resource
```

Kubernetes does not know how to delete something in an external system.

That is where a finalizer can help.

---

# Deletion Propagation

Kubernetes supports different deletion propagation strategies.

For example:

```bash
kubectl delete greeting hello \
  --cascade=background
```

or:

```bash
kubectl delete greeting hello \
  --cascade=foreground
```

The details differ in when the owner and dependents are removed.

For this stage of the project, the important idea is that the ownership relationship gives Kubernetes enough information to manage the dependent lifecycle.

---

# RBAC

The operator runs using:

```text
platform-system/greeting-operator
```

as its ServiceAccount.

The authorization chain is:

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

The operator currently has permissions to:

```text
get/list/watch Greetings
update Greeting status

get/list/watch ConfigMaps
create/update/patch/delete ConfigMaps

create/patch Kubernetes Events
```

---

# Running the Operator

Create the cluster:

```bash
task cluster:create
```

Install the CRD:

```bash
task crd:install
```

Build the operator image:

```bash
task operator:image:build
```

Load the image into Kind:

```bash
task operator:image:load
```

Deploy:

```bash
task operator:deploy
```

Check:

```bash
task operator:status
```

Follow logs:

```bash
task operator:logs
```

---

# Greeting Commands

Create:

```bash
task greeting:create
```

List:

```bash
task greeting:get
```

Check status:

```bash
task greeting:status
```

Check Events:

```bash
task greeting:events
```

Inspect the managed ConfigMap:

```bash
task greeting:configmap
```

Manually reconcile:

```bash
task greeting:reconcile
```

Manually reconcile another Greeting:

```bash
task greeting:reconcile GREETING_NAME=<name>
```

Delete the Greeting:

```bash
task greeting:delete
```

---

# Testing the Resource Lifecycle

A useful experiment is:

```bash
task greeting:create
```

Verify the ConfigMap:

```bash
task greeting:configmap
```

Then delete only the ConfigMap:

```bash
kubectl delete configmap hello-greeting
```

Check again:

```bash
kubectl get configmap hello-greeting
```

The ConfigMap should be recreated.

Now delete the Greeting:

```bash
task greeting:delete
```

Finally check:

```bash
kubectl get configmap hello-greeting
```

The ConfigMap should eventually no longer exist.

This demonstrates both reconciliation and Kubernetes garbage collection.

---

# Current Reconciliation and Lifecycle Flow

The operator now supports several lifecycle paths.

## Desired state change

```text
Greeting.spec changes
       |
       v
generation changes
       |
       v
reconcile
```

## Manual reconciliation

```text
reconcile-at changes
       |
       v
update filter
       |
       v
reconcile
```

## Failure retry

```text
reconciliation fails
       |
       v
automatic retry
```

## Dependent resource deletion

```text
ConfigMap deleted
       |
       v
Greeting still exists
       |
       v
reconcile
       |
       v
ConfigMap recreated
```

## Owner deletion

```text
Greeting deleted
       |
       v
ownerReference
       |
       v
Kubernetes garbage collection
       |
       v
ConfigMap deleted
```

---

# Current Architecture

```text
                         Greeting
                            |
              +-------------+-------------+
              |                           |
          spec change              reconcile-at change
              |                           |
              +-------------+-------------+
                            |
                            v
                       reconcile()
                            |
                            v
                        ConfigMap
                            |
             +--------------+--------------+
             |                             |
      ConfigMap deleted             Greeting deleted
             |                             |
             v                             v
        reconcile                   garbage collection
             |                             |
             v                             v
      ConfigMap recreated            ConfigMap removed
```

The operator now demonstrates both:

```text
desired-state reconciliation
```

and:

```text
ownership-based lifecycle management
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
                ├── GreetingUpdateFilter.java
                ├── GreetingConfigMapDependentResource.java
                └── GreetingOperatorApplication.java
```

Related Kubernetes resources:

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

# What's Next?

The current dependent resource exists entirely inside Kubernetes.

Because of that, owner references and Kubernetes garbage collection can handle most of its lifecycle cleanup.

The next topic is:

```text
finalizers
```

Finalizers become useful when deletion requires work that Kubernetes cannot perform automatically.

For example:

```text
Greeting deletion requested
        |
        v
deletionTimestamp set
        |
        v
controller performs cleanup
        |
        v
finalizer removed
        |
        v
Greeting deletion completes
```

That will let the project explore the difference between:

```text
Kubernetes-owned cleanup
```

and:

```text
controller-managed cleanup
```

After that, the next areas include:

```text
integration tests
GitHub Actions
higher-level platform APIs
```

The Greeting Operator now demonstrates reconciliation, failure recovery, manual reconciliation, resource ownership, and Kubernetes-native garbage collection.
