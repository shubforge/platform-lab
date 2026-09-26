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

The operator also reports its state back through the `Greeting.status` field.

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

The controller declares what should exist rather than manually implementing separate create and update flows.

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

This allows the state of the resource to be inspected without reading operator logs.

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

is supplied by the user.

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

when the desired configuration changes.

The operator reports the generation it has processed using:

```yaml
status:
  observedGeneration:
```

For example:

```text
generation          = 2
observedGeneration  = 2
```

means the current status represents the latest generation processed by the operator.

If:

```text
generation          = 3
observedGeneration  = 2
```

the desired resource has changed, but the operator status still represents an older generation.

---

# Conditions

The operator currently exposes one condition:

```text
Ready
```

A successful reconciliation produces:

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

This gives the resource a simple lifecycle:

```text
             success
                |
                v
          +-------------+
          | Ready=True  |
          +-------------+
                |
              failure
                |
                v
          +-------------+
          | Ready=False |
          +-------------+
```

If a later reconciliation succeeds, the condition moves back to:

```text
Ready=True
```

---

# lastTransitionTime

Conditions include:

```yaml
lastTransitionTime:
```

This represents when the condition changed state.

For example:

```text
Ready=False
     |
     v
Ready=True
```

is a transition.

But:

```text
Ready=True
     |
     v
reconcile again
     |
     v
Ready=True
```

is not.

The existing transition time is therefore kept while the condition status remains unchanged.

---

# Failure Handling

Failures during reconciliation are handled using the Java Operator SDK error status mechanism.

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

The operator updates the resource status with:

```yaml
status:

  conditions:
    - type: Ready
      status: "False"
      reason: ReconciliationFailed
      message: ...
```

The complete exception is written to the operator logs.

The status message is kept shorter and intended to explain the failure from the resource user's perspective.

---

# Retry Behavior

A reconciliation exception causes Java Operator SDK to retry the reconciliation automatically.

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

The retries use backoff and are limited.

This is important.

The operator does not retry a failed resource forever.

A failure experiment produced events similar to:

```text
Warning  ReconciliationFailed  ... (x6 over ...)
```

which showed:

```text
initial attempt
      +
automatic retries
      |
      v
retry limit reached
```

After the retry limit is reached, the operator waits for another relevant reconciliation trigger.

---

## External Changes Do Not Automatically Trigger Reconciliation

One experiment was to temporarily remove permission for the operator to create ConfigMaps.

The reconciliation failed with:

```text
403 Forbidden
```

and the operator retried several times.

After the retries were exhausted, the RBAC permission was restored.

However, the resource was not immediately reconciled.

That is because changing:

```text
ClusterRole
```

does not create an event for the `Greeting` controller.

The controller watches resources relevant to its reconciliation flow, not the ClusterRole itself.

The sequence was:

```text
Greeting created
      |
      v
ConfigMap creation fails
      |
      v
retry
      |
      v
retry limit reached
      |
      v
RBAC fixed
      |
      v
no Greeting event
      |
      v
no immediate reconciliation
```

Changing the Greeting specification created a new event:

```text
Greeting spec updated
      |
      v
generation changed
      |
      v
new reconciliation
      |
      v
ConfigMap created
      |
      v
Ready=True
```

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
a new event asking the controller to evaluate the resource again
```

A more complete manual reconciliation strategy will be explored separately.

---

# Kubernetes Events

The operator also records Kubernetes Events.

This makes important controller activity visible using normal Kubernetes tooling.

Successful reconciliation produces a normal event:

```text
Normal  Reconciled
```

with a message similar to:

```text
Managed ConfigMap hello-greeting is in the desired state
```

A failed reconciliation produces:

```text
Warning  ReconciliationFailed
```

with the failure message.

Inspect events using:

```bash
kubectl describe greeting hello
```

Example:

```text
Events:
  Type     Reason                  Message
  ----     ------                  -------
  Normal   Reconciled              Managed ConfigMap hello-greeting is in the desired state
```

During failure:

```text
Events:
  Type     Reason                  Message
  ----     ------                  -------
  Warning  ReconciliationFailed    ...
```

Repeated equivalent events may appear with a count instead of being printed as completely separate events.

For example:

```text
ReconciliationFailed ... (x6 over 5m31s)
```

---

# Logging vs Status vs Events

The operator now exposes information through three different mechanisms.

```text
Logs
=
developer/operator debugging
```

```text
Status
=
current state of the custom resource
```

```text
Events
=
important things that happened to the resource
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
        |
        v
Kubernetes Event
```

Each serves a different purpose.

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

The ClusterRole allows the operator to:

```text
get/list/watch Greetings
update Greeting status

get/list/watch ConfigMaps
create/update/patch/delete ConfigMaps

create/patch Kubernetes Events
```

---

## Greeting Status Permissions

Because status is a Kubernetes subresource, it has separate RBAC permissions:

```yaml
- apiGroups:
    - platform.shubforge.dev

  resources:
    - greetings/status

  verbs:
    - get
    - patch
    - update
```

---

## Event Permissions

To record Kubernetes Events, the operator also needs:

```yaml
- apiGroups:
    - ""

  resources:
    - events

  verbs:
    - get
    - create
    - patch
```

---

# Verify RBAC

Check Greeting access:

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
  --namespace default \
  --as=system:serviceaccount:platform-system:greeting-operator
```

Expected:

```text
yes
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

# Create a Greeting

Create the sample resource:

```bash
task greeting:create
```

Check:

```bash
task greeting:get
```

Inspect complete status:

```bash
task greeting:status
```

or:

```bash
kubectl get greeting hello -o yaml
```

Check Events:

```bash
task greeting:events
```

or:

```bash
kubectl describe greeting hello
```

---

# Successful Flow

A successful reconciliation looks like:

```text
Greeting
    |
    v
Operator
    |
    v
ConfigMap
    |
    +------> Ready=True
    |
    +------> Reconciled Event
```

Example status:

```yaml
status:

  configMapName: hello-greeting

  observedGeneration: 1

  conditions:
    - type: Ready
      status: "True"
      reason: ConfigMapReady
      message: Managed ConfigMap hello-greeting is in the desired state
```

---

# Failure Flow

A failed reconciliation looks like:

```text
Greeting
    |
    v
Operator
    |
    v
Failure
    |
    +------> Ready=False
    |
    +------> Warning Event
    |
    +------> retry
```

If retries continue failing:

```text
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
    |
    v
retry limit reached
```

The resource remains in its failed state until another relevant reconciliation is triggered.

---

# Testing Failure Handling

One way to test failure handling is to temporarily remove ConfigMap creation permission from the operator ClusterRole.

Temporarily remove:

```text
create
update
patch
delete
```

from the ConfigMap verbs.

Then create a new Greeting:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: failure-test

spec:
  message: "Failure test"
```

Check status:

```bash
kubectl get greeting failure-test -o yaml
```

The resource should eventually report:

```yaml
conditions:

  - type: Ready
    status: "False"
    reason: ReconciliationFailed
```

Check Events:

```bash
kubectl describe greeting failure-test
```

The output should include:

```text
Warning  ReconciliationFailed
```

---

# Testing Recovery

Restore the correct RBAC configuration:

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

If automatic retries have already been exhausted, restoring RBAC alone does not immediately cause another Greeting reconciliation.

For the current experiment, changing the Greeting specification creates a new reconciliation event.

For example:

```bash
kubectl patch greeting failure-test \
  --type=merge \
  -p '{"spec":{"message":"Recovery test"}}'
```

The new sequence becomes:

```text
RBAC restored
      |
      v
Greeting spec changed
      |
      v
new reconciliation
      |
      v
ConfigMap created
      |
      v
Ready=True
      |
      v
Reconciled Event
```

Check:

```bash
kubectl get greetings
```

and:

```bash
kubectl describe greeting failure-test
```

---

# kubectl Output

The CRD exposes useful printer columns.

```bash
kubectl get greetings
```

shows:

```text
NAME           READY   CONFIGMAP                  AGE
hello          True    hello-greeting             5m
failure-test   False   <none>                     1m
```

After recovery:

```text
NAME           READY   CONFIGMAP                  AGE
hello          True    hello-greeting             6m
failure-test   True    failure-test-greeting      2m
```

---

# Taskfile

The root Taskfile acts as the developer interface for the repository.

List commands:

```bash
task --list
```

Useful Greeting commands include:

```bash
task greeting:create
task greeting:get
task greeting:status
task greeting:events
task greeting:delete
```

Useful operator commands include:

```bash
task operator:build
task operator:image:build
task operator:image:load
task operator:deploy
task operator:restart
task operator:status
task operator:logs
```

---

# Current Architecture

The current controller flow is:

```text
                     Greeting
                         |
                         v
                  Greeting Operator
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
            Normal Event       retry
```

The operator now does more than create Kubernetes resources.

It also reports:

```text
current state
failures
retries
important events
```

through Kubernetes-native mechanisms.

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

The next steps will be kept separate so each operator concept can be explored properly.

Areas to explore include:

```text
reconciliation best practices
manual reconciliation
periodic reconciliation
owner references
deletion behavior
finalizers
integration tests
GitHub Actions
```

For now, the Greeting Operator can handle both the successful and failed reconciliation paths and expose that information directly through Kubernetes.
