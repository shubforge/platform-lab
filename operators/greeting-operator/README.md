# Greeting Operator

The Greeting Operator is the first Kubernetes operator built as part of Platform Lab.

It is a small learning project used to understand:

- Custom Resource Definitions
- Java Operator SDK
- Kubernetes controllers
- dependent resources
- status and conditions
- ServiceAccounts and RBAC
- failure handling
- retry behavior
- Kubernetes Events
- manual reconciliation
- event filtering

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

The operator also reports its current state through `Greeting.status`.

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

The primary reconciler is implemented in:

```text
GreetingReconciler.java
```

The managed ConfigMap is implemented as a dependent resource:

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

The controller declares the desired resource instead of manually implementing separate create and update flows.

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

This allows the current state to be inspected without reading operator logs.

---

## Spec vs Status

A useful way to think about the resource is:

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

For example:

```yaml
spec:
  message: "Hello from Platform Lab"
```

is provided by the user.

The operator reports:

```yaml
status:
  configMapName: hello-greeting
```

Conceptually:

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

Kubernetes maintains:

```yaml
metadata:
  generation:
```

for the desired configuration.

When the `spec` changes, the generation normally increases.

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

means the current status represents the latest desired state processed by the operator.

If:

```text
generation          = 3
observedGeneration  = 2
```

the desired state has changed, but the operator status still represents an older generation.

---

# Conditions

The operator currently exposes a `Ready` condition.

Successful reconciliation produces:

```yaml
conditions:

  - type: Ready
    status: "True"
    reason: ConfigMapReady
    message: Managed ConfigMap hello-greeting is in the desired state
```

A failed reconciliation produces:

```yaml
conditions:

  - type: Ready
    status: "False"
    reason: ReconciliationFailed
    message: ...
```

The resource can therefore move between:

```text
Ready=True
```

and:

```text
Ready=False
```

depending on reconciliation outcome.

---

# Failure Handling

Failures during reconciliation are handled using the Java Operator SDK error-status mechanism.

When reconciliation fails:

```text
Greeting
    |
    v
Operator
    |
    v
Exception
    |
    +------> Ready=False
    |
    +------> Warning Event
    |
    +------> Retry
```

The full exception is written to the operator logs.

The resource status receives a shorter user-facing error message.

---

# Retry Behavior

A failed reconciliation is retried automatically by the Java Operator SDK.

Conceptually:

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

During the RBAC failure experiment, Kubernetes Events showed:

```text
Warning  ReconciliationFailed  ... (x6 over ...)
```

which showed that the same reconciliation had failed multiple times.

After the retry limit was reached, the operator waited for another relevant reconciliation trigger.

---

# Retry vs Reconciliation Trigger

These are two different concepts.

```text
Retry
=
another attempt after a failed reconciliation
```

while:

```text
Reconciliation trigger
=
something changed that causes the controller
to evaluate the resource again
```

For example:

```text
Greeting spec changed
        |
        v
new reconciliation
```

But changing an unrelated resource such as:

```text
ClusterRole
```

does not automatically trigger the Greeting controller.

This became important when testing recovery from an RBAC failure.

---

# Kubernetes Events

The operator records Kubernetes Events for important reconciliation outcomes.

Successful reconciliation produces:

```text
Normal  Reconciled
```

A failed reconciliation produces:

```text
Warning  ReconciliationFailed
```

Events can be inspected using:

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

The:

```text
x2
```

means Kubernetes aggregated two equivalent reconciliation Events.

---

# Manual Reconciliation

After testing failure recovery, one problem became clear.

If automatic retries are exhausted and an external dependency is fixed later, such as RBAC, there may be no new event for the `Greeting` controller.

Changing the `Greeting.spec` works, but changing business configuration only to trigger reconciliation is not ideal.

The operator therefore supports an explicit manual reconciliation annotation:

```text
platform.shubforge.dev/reconcile-at
```

Example:

```bash
kubectl annotate greeting hello \
  platform.shubforge.dev/reconcile-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --overwrite
```

The timestamp changes every time the command is executed.

The operator detects that annotation change and reconciles the existing desired state again.

---

## Manual Reconcile Flow

The flow looks like:

```text
External problem
      |
      v
retries exhausted
      |
      v
problem fixed
      |
      v
reconcile-at annotation changed
      |
      v
Greeting update event
      |
      v
reconcile()
      |
      v
resource evaluated again
```

This allows reconciliation to be triggered without modifying:

```yaml
spec:
```

---

# Generation vs Manual Reconciliation

Manual reconciliation does not change the desired configuration.

Because only metadata changes:

```yaml
metadata:
  annotations:
    platform.shubforge.dev/reconcile-at: ...
```

the resource generation remains unchanged.

For example:

```text
generation          = 1
observedGeneration  = 1
```

before manual reconciliation.

After:

```bash
task greeting:reconcile
```

the resource can still show:

```text
generation          = 1
observedGeneration  = 1
```

This is expected.

The manual reconciliation means:

```text
Re-evaluate generation 1
```

not:

```text
There is a new desired state
```

This gives a useful distinction:

```text
generation
=
version of desired spec
```

while:

```text
reconcile-at
=
request to re-evaluate that desired spec
```

---

# Greeting Update Filter

The operator uses a custom update filter.

The goal is to reconcile when:

```text
spec generation changes
```

or when:

```text
reconcile-at annotation changes
```

but ignore updates such as status changes that should not create another reconciliation loop.

Conceptually:

```text
generation changed?
       |
       yes
       |
       v
   reconcile


reconcile-at changed?
       |
       yes
       |
       v
   reconcile


status/resourceVersion only changed?
       |
       no
       |
       v
    ignore
```

The filter is implemented in:

```text
GreetingUpdateFilter.java
```

Conceptually, the logic is:

```java
return generationChanged(newResource, oldResource)
        || reconcileAnnotationChanged(newResource, oldResource);
```

This prevents every metadata or status update from automatically triggering reconciliation.

---

# Manual Reconciliation with Taskfile

A Taskfile command is available:

```bash
task greeting:reconcile
```

The command updates:

```text
platform.shubforge.dev/reconcile-at
```

with the current timestamp.

By default it reconciles:

```text
hello
```

A different Greeting can be selected using:

```bash
task greeting:reconcile GREETING_NAME=failure-test
```

or:

```bash
task greeting:reconcile GREETING_NAME=manual-reconcile-test
```

---

# Testing Manual Reconciliation

Run:

```bash
task greeting:reconcile
```

Then inspect the annotation:

```bash
kubectl get greeting hello \
  -o jsonpath='{.metadata.annotations.platform\.shubforge\.dev/reconcile-at}'
```

Check Events:

```bash
kubectl describe greeting hello
```

Example:

```text
Normal  Reconciled  5s (x2 over 2m12s)
```

The Event count increasing confirms that another reconciliation occurred.

The generation can still remain:

```text
1
```

because the desired `spec` did not change.

---

# Testing Recovery Using Manual Reconciliation

A useful failure experiment is to remove ConfigMap creation permission temporarily.

Edit the ClusterRole:

```bash
kubectl edit clusterrole greeting-operator
```

Temporarily change:

```yaml
verbs:
  - get
  - list
  - watch
  - create
  - update
  - patch
  - delete
```

to:

```yaml
verbs:
  - get
  - list
  - watch
```

Verify:

```bash
kubectl auth can-i \
  create configmaps \
  --namespace default \
  --as=system:serviceaccount:platform-system:greeting-operator
```

Expected:

```text
no
```

Create a test Greeting:

```bash
kubectl apply -f - <<'EOF'
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: manual-reconcile-test

spec:
  message: "Manual reconciliation test"
EOF
```

The operator will fail to create the ConfigMap.

Eventually:

```bash
kubectl get greeting manual-reconcile-test
```

should show:

```text
READY
False
```

and:

```bash
kubectl describe greeting manual-reconcile-test
```

should show `ReconciliationFailed` Events.

---

# Recovering Without Changing Spec

Restore the repository RBAC configuration:

```bash
task operator:deploy
```

Verify:

```bash
kubectl auth can-i \
  create configmaps \
  --namespace default \
  --as=system:serviceaccount:platform-system:greeting-operator
```

Expected:

```text
yes
```

Instead of modifying `spec`, trigger reconciliation manually:

```bash
task greeting:reconcile \
  GREETING_NAME=manual-reconcile-test
```

The flow becomes:

```text
RBAC fixed
      |
      v
manual reconcile requested
      |
      v
reconcile-at changes
      |
      v
GreetingUpdateFilter
      |
      v
reconcile()
      |
      v
ConfigMap created
      |
      v
Ready=True
```

Verify:

```bash
kubectl get greeting manual-reconcile-test
```

and:

```bash
kubectl get configmap manual-reconcile-test-greeting
```

Then inspect Events:

```bash
kubectl describe greeting manual-reconcile-test
```

You should see the failed history followed by a successful reconciliation.

---

# Logs vs Status vs Events

The operator now exposes information through three different mechanisms.

```text
Logs
=
developer/operator debugging
```

```text
Status
=
current resource state
```

```text
Events
=
important resource history
```

For example:

```text
Exception stack trace
        |
        v
Operator Logs
```

while:

```text
Ready=False
ReconciliationFailed
        |
        v
Greeting Status
```

and:

```text
Warning ReconciliationFailed
Normal Reconciled
        |
        v
Kubernetes Events
```

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

The current permissions allow the operator to:

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

Manually reconcile:

```bash
task greeting:reconcile
```

Manually reconcile another Greeting:

```bash
task greeting:reconcile GREETING_NAME=<name>
```

Delete:

```bash
task greeting:delete
```

---

# Current Reconciliation Triggers

The controller currently reacts to several kinds of situations.

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

## Managed resource event

```text
ConfigMap changes/disappears
       |
       v
dependent resource event
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

## Manual reconciliation

```text
reconcile-at annotation changes
       |
       v
GreetingUpdateFilter
       |
       v
reconcile
```

These are different mechanisms, even though they all eventually lead to:

```text
reconcile()
```

---

# Current Architecture

The controller flow now looks like:

```text
                         Greeting
                            |
              +-------------+-------------+
              |                           |
          spec change              reconcile-at change
              |                           |
              v                           v
      generation change             update filter
              |                           |
              +-------------+-------------+
                            |
                            v
                       reconcile()
                            |
                     +------+------+
                     |             |
                  success        failure
                     |             |
                     v             v
                 ConfigMap     Ready=False
                     |             |
                     v             v
                 Ready=True    Warning Event
                     |             |
                     v             v
               Normal Event       Retry
```

The operator now supports both automatic and explicit reconciliation triggers.

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

Manual reconciliation solves one specific problem:

```text
external issue fixed
+
automatic retries exhausted
+
no relevant resource event
```

The next topics can now focus on reconciliation behavior in more depth:

```text
reconciliation best practices
periodic reconciliation
rescheduling
idempotency
external dependency handling
```

After that, the operator lifecycle can be expanded with:

```text
owner references
resource deletion
finalizers
integration tests
GitHub Actions
```

The Greeting Operator now supports both normal desired-state reconciliation and an explicit way to request another reconciliation when needed.
