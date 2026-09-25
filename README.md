# Platform Lab

A hands-on learning project for exploring Kubernetes, platform engineering, operators, cloud-native architecture, and developer platforms.

The goal is to learn these concepts by building them step by step.

The project starts with a small Kubernetes Custom Resource and gradually adds controllers, reconciliation, RBAC, deployment, testing, and higher-level platform APIs.

---

## Current Progress

- [x] Repository setup
- [x] SDKMAN Java environment
- [x] Maven Wrapper
- [x] Taskfile
- [x] Local Kind cluster
- [x] Greeting Custom Resource Definition
- [x] Sample Greeting resource
- [x] Java Greeting Controller
- [x] ConfigMap creation from Greeting
- [x] Build executable operator JAR
- [x] Build operator Docker image
- [x] Load operator image into Kind
- [x] ServiceAccount
- [x] ClusterRole and ClusterRoleBinding
- [x] Run operator inside Kubernetes
- [x] ConfigMap reconciliation
- [ ] Greeting status and conditions
- [ ] Controller unit tests
- [ ] Operator integration tests
- [ ] GitHub Actions
- [ ] Application API
- [ ] ApplicationRelease
- [ ] API dependencies and access grants

---

## Current Architecture

The current flow looks like this:

```text
Greeting
    |
    v
Kubernetes API
    |
    v
Greeting Operator
    |
    v
ConfigMap
```

The operator itself now runs inside Kubernetes:

```text
Kind Kubernetes Cluster

+--------------------------------------+
|                                      |
| platform-system                      |
|                                      |
|   greeting-operator Deployment       |
|              |                       |
|              v                       |
|            Pod                       |
|              |                       |
|              v                       |
|       ServiceAccount                 |
|                                      |
+--------------|-----------------------+
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

The operator can watch `Greeting` resources and manage ConfigMaps across namespaces.

---

## Prerequisites

The following tools are used for local development:

- Docker
- SDKMAN
- Java
- Maven Wrapper
- kubectl
- Kind
- Task

Check the required tools using:

```bash
task tools:check
```

---

# Java Setup

The Java version used by the project is defined in:

```text
.sdkmanrc
```

Load the project Java environment:

```bash
sdk env
```

If the required version is not installed:

```bash
sdk env install
```

---

# Maven

The Maven Wrapper is kept at the repository root:

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

It can be built directly using:

```bash
./mvnw \
  -f operators/greeting-operator/pom.xml \
  clean package
```

Normally this is run through the Taskfile:

```bash
task operator:build
```

---

# Local Kubernetes Cluster

The project uses Kind for running Kubernetes locally.

Create the cluster:

```bash
task cluster:create
```

Check the cluster:

```bash
task cluster:status
```

Verify the current context:

```bash
kubectl config current-context
```

Expected:

```text
kind-platform-lab
```

Delete the cluster:

```bash
task cluster:delete
```

---

# Project Structure

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
│   ├── operator/
│   │   ├── namespace.yaml
│   │   ├── service-account.yaml
│   │   ├── rbac.yaml
│   │   └── deployment.yaml
│   │
│   └── samples/
│       └── greeting.yaml
│
├── operators/
│   └── greeting-operator/
│       ├── Dockerfile
│       ├── pom.xml
│       │
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
├── .gitattributes
├── .gitignore
├── .sdkmanrc
├── mvnw
├── mvnw.cmd
├── README.md
└── Taskfile.yml
```

The structure will continue to evolve as more platform capabilities are added.

---

# Greeting Custom Resource

The first custom Kubernetes API in Platform Lab is called `Greeting`.

Example:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: hello

spec:
  message: "Hello from Platform Lab"
```

The API uses:

```text
Group:   platform.shubforge.dev
Version: v1alpha1
Kind:    Greeting
```

The resource is namespace scoped.

---

## Greeting CRD

The CRD is available at:

```text
k8s/crds/greetings.yaml
```

Install it using:

```bash
task crd:install
```

Verify:

```bash
kubectl get crds
```

Expected:

```text
greetings.platform.shubforge.dev
```

---

## Create a Greeting

A sample resource is available at:

```text
k8s/samples/greeting.yaml
```

Create it:

```bash
task greeting:create
```

List Greetings:

```bash
task greeting:get
```

Example:

```text
NAME    AGE
hello   10s
```

Inspect it:

```bash
task greeting:describe
```

or:

```bash
kubectl get greeting hello -o yaml
```

The CRD also defines:

```yaml
shortNames:
  - greet
```

so this also works:

```bash
kubectl get greet
```

---

# Greeting Controller

A CRD gives Kubernetes a new API, but it does not add behavior.

Without a controller:

```text
Greeting
    |
    v
Kubernetes API
    |
    v
Stored
```

The Greeting controller adds behavior:

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

# Java Resource Model

The Kubernetes resource is represented in Java by `Greeting`.

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

The `spec` section is represented by `GreetingSpec`.

For now it contains:

```java
private String message;
```

So this YAML:

```yaml
spec:
  message: "Hello from Platform Lab"
```

can be accessed in Java using:

```java
greeting.getSpec().getMessage()
```

`GreetingStatus` currently exists as a placeholder and will be expanded later.

---

# Desired ConfigMap

The managed ConfigMap is defined by:

```text
GreetingConfigMapDependentResource.java
```

The controller describes what the ConfigMap should look like.

Conceptually:

```text
Greeting
    |
    v
desired()
    |
    v
ConfigMap
```

Instead of manually implementing:

```text
if ConfigMap does not exist
    create it

if ConfigMap changed
    update it
```

the operator declares the desired resource and lets the reconciliation process keep Kubernetes aligned with that desired state.

---

# Building the Operator

Build the Java application:

```bash
task operator:build
```

This creates the operator JAR under:

```text
operators/greeting-operator/target/
```

The executable application can be started using:

```bash
java -jar <operator-jar>
```

For normal development, use the Taskfile commands instead.

---

# Building the Docker Image

The operator Dockerfile is located at:

```text
operators/greeting-operator/Dockerfile
```

Build the image:

```bash
task operator:image:build
```

The development image is:

```text
greeting-operator:dev
```

Because the project uses Kind, the locally built image needs to be loaded into the Kind cluster:

```bash
task operator:image:load
```

The build flow is:

```text
Java Source
    |
    v
Maven
    |
    v
Executable JAR
    |
    v
Docker
    |
    v
greeting-operator:dev
    |
    v
Kind
```

---

# Running the Operator Inside Kubernetes

Previously, the operator ran locally:

```text
Laptop
   |
   v
Java Controller
   |
   | kubeconfig
   v
Kind Cluster
```

It now runs as a Kubernetes Deployment:

```text
Kind Cluster
    |
    v
greeting-operator Deployment
    |
    v
Pod
```

The Kubernetes manifests are located under:

```text
k8s/operator/
```

---

# Operator Namespace

Platform components run inside:

```text
platform-system
```

The namespace is created by:

```text
k8s/operator/namespace.yaml
```

The operator can live in `platform-system` while managing resources in other namespaces.

---

# ServiceAccount

Inside Kubernetes, the operator no longer uses the developer's local kubeconfig.

Instead, the Pod uses a Kubernetes `ServiceAccount`.

```yaml
apiVersion: v1
kind: ServiceAccount

metadata:
  name: greeting-operator
  namespace: platform-system
```

The Deployment connects to it using:

```yaml
serviceAccountName: greeting-operator
```

The identity chain is:

```text
Operator Pod
     |
     v
ServiceAccount
platform-system/greeting-operator
```

The ServiceAccount provides the identity, but it does not define what that identity can do.

Permissions are defined using RBAC.

---

# Kubernetes RBAC

The Greeting operator needs permission to:

```text
watch Greetings
read Greetings
update Greetings
manage ConfigMaps
```

The project uses:

```text
ClusterRole
+
ClusterRoleBinding
```

---

## ClusterRole

The `ClusterRole` defines the permissions.

For Greetings:

```yaml
- apiGroups:
    - platform.shubforge.dev

  resources:
    - greetings

  verbs:
    - get
    - list
    - watch
    - patch
    - update
```

For ConfigMaps:

```yaml
- apiGroups:
    - ""

  resources:
    - configmaps

  verbs:
    - get
    - list
    - watch
    - create
    - update
    - patch
    - delete
```

The ClusterRole answers:

```text
What operations are allowed?
```

---

## ClusterRoleBinding

The `ClusterRoleBinding` connects the operator's ServiceAccount to the ClusterRole.

Conceptually:

```text
ServiceAccount
platform-system/greeting-operator

        |
        v

ClusterRoleBinding

        |
        v

ClusterRole
greeting-operator
```

The binding answers:

```text
Who gets these permissions?
```

The relationship becomes:

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
     +---- watch Greetings
     |
     +---- create ConfigMaps
     |
     +---- update ConfigMaps
     |
     └---- delete ConfigMaps
```

---

## Access Across Namespaces

The ServiceAccount exists in:

```text
platform-system
```

but that does not mean it can only access resources in that namespace.

Its permissions are determined by RBAC.

Because the project uses:

```text
ClusterRole
+
ClusterRoleBinding
```

the operator can use those permissions across namespaces.

For example:

```text
platform-system
    |
    └── greeting-operator Pod

default
    |
    ├── Greeting
    └── ConfigMap

demo
    |
    ├── Greeting
    └── ConfigMap

team-a
    |
    ├── Greeting
    └── ConfigMap
```

The operator remains in:

```text
platform-system
```

while managing resources elsewhere.

---

## RoleBinding vs ClusterRoleBinding

A useful way to think about RBAC is:

```text
Role
    permissions defined for a namespace

ClusterRole
    reusable or cluster-wide permission definition

RoleBinding
    grants permissions inside one namespace

ClusterRoleBinding
    grants ClusterRole permissions cluster-wide
```

For example:

```text
ClusterRole
+
ClusterRoleBinding

= cluster-wide access
```

while:

```text
ClusterRole
+
RoleBinding in team-a

= access limited to team-a
```

This gives a way to reuse the same ClusterRole while limiting where it applies.

---

## Verify RBAC Permissions

The permissions can be checked using:

```bash
kubectl auth can-i \
  list greetings.platform.shubforge.dev \
  --as=system:serviceaccount:platform-system:greeting-operator \
  --all-namespaces
```

Expected:

```text
yes
```

Check ConfigMap creation:

```bash
kubectl auth can-i \
  create configmaps \
  --as=system:serviceaccount:platform-system:greeting-operator \
  --all-namespaces
```

Expected:

```text
yes
```

A particular namespace can also be tested:

```bash
kubectl auth can-i \
  create configmaps \
  --namespace default \
  --as=system:serviceaccount:platform-system:greeting-operator
```

---

# RBAC Scope vs Controller Watch Scope

These are two different concepts.

```text
RBAC
=
What is the operator allowed to access?
```

while:

```text
Controller configuration
=
What resources does the controller actually watch?
```

An operator could have cluster-wide RBAC while being configured to watch only one namespace.

For this learning project, the goal is to let the Greeting operator work across namespaces.

Later, the permissions can be restricted further where appropriate.

---

# Deploy the Operator

Deploy all operator Kubernetes resources:

```bash
task operator:deploy
```

Check the operator:

```bash
task operator:status
```

or:

```bash
kubectl get pods -n platform-system
```

Expected:

```text
NAME                                 READY   STATUS
greeting-operator-xxxxxxxxxx-xxxxx   1/1     Running
```

Follow logs:

```bash
task operator:logs
```

At this point the operator runs inside Kubernetes, so there is no need to run:

```bash
task operator:run
```

from the laptop.

---

# Running the Complete Example

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

Deploy the operator:

```bash
task operator:deploy
```

Verify:

```bash
task operator:status
```

Create the Greeting:

```bash
task greeting:create
```

Check it:

```bash
task greeting:get
```

Check the generated ConfigMap:

```bash
kubectl get configmap hello-greeting
```

Inspect it:

```bash
kubectl get configmap hello-greeting -o yaml
```

Expected data:

```yaml
data:
  message: Hello from Platform Lab
```

---

# Testing Reconciliation

Delete the managed ConfigMap manually:

```bash
kubectl delete configmap hello-greeting
```

Check again:

```bash
kubectl get configmap hello-greeting
```

The operator should recreate it.

The desired state still says:

```text
Greeting exists
        |
        v
ConfigMap should exist
```

but the actual state temporarily became:

```text
Greeting exists
ConfigMap missing
```

The controller reconciles the actual state back toward the desired state:

```text
Desired State
      |
      v
Controller
      |
      v
Actual State
```

---

# Testing Across Namespaces

Create another namespace:

```bash
kubectl create namespace demo
```

Create a Greeting there:

```yaml
apiVersion: platform.shubforge.dev/v1alpha1
kind: Greeting

metadata:
  name: hello
  namespace: demo

spec:
  message: "Hello from demo namespace"
```

The operator should be able to manage the corresponding ConfigMap in the same namespace.

Check:

```bash
kubectl get greetings -n demo
```

and:

```bash
kubectl get configmaps -n demo
```

This demonstrates that the operator can run in:

```text
platform-system
```

while managing resources in other namespaces.

---

# Why There Is No Kubernetes Service

The operator currently does not expose an application API that another workload needs to call.

Its main communication path is:

```text
Operator
    |
    v
Kubernetes API
```

Therefore the current setup does not require:

```text
Service
Ingress
Gateway
```

The operator is simply a long-running Kubernetes workload watching and reconciling resources.

---

# Taskfile

The root `Taskfile.yml` provides a common interface for local development.

List commands:

```bash
task --list
```

## Tooling

```bash
task tools:check
```

## Cluster

Create:

```bash
task cluster:create
```

Status:

```bash
task cluster:status
```

Delete:

```bash
task cluster:delete
```

## CRD

Install:

```bash
task crd:install
```

Delete:

```bash
task crd:delete
```

## Greeting

Create:

```bash
task greeting:create
```

List:

```bash
task greeting:get
```

Describe:

```bash
task greeting:describe
```

Delete:

```bash
task greeting:delete
```

## Operator

Build Java application:

```bash
task operator:build
```

Run locally:

```bash
task operator:run
```

Build Docker image:

```bash
task operator:image:build
```

Load image into Kind:

```bash
task operator:image:load
```

Deploy inside Kubernetes:

```bash
task operator:deploy
```

Check status:

```bash
task operator:status
```

Follow logs:

```bash
task operator:logs
```

Remove operator:

```bash
task operator:undeploy
```

---

# What We Have Built So Far

The project started with only:

```text
CRD
 |
 v
Greeting
```

Then behavior was added:

```text
Greeting
    |
    v
Java Controller
    |
    v
ConfigMap
```

The controller initially ran locally:

```text
Laptop
    |
    v
Java Controller
    |
    v
Kind
```

It now runs completely inside Kubernetes:

```text
Greeting
    |
    v
Greeting Operator Pod
    |
    v
ConfigMap
```

with Kubernetes-native identity and authorization:

```text
Pod
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

---

# What's Next?

The next steps are to make the controller behavior more complete and observable.

Planned areas include:

- Greeting `status`
- Kubernetes conditions
- reconciliation details
- owner references
- error handling
- retry behavior
- finalizers
- controller unit tests
- operator integration tests
- GitHub Actions

After that, the same concepts can be applied to more useful platform abstractions such as:

```text
Application
ApplicationRelease
ApiDependency
AccessGrant
```

The simple `Greeting` resource remains the learning foundation for those larger platform ideas.
