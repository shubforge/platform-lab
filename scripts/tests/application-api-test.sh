#!/usr/bin/env bash

set -euo pipefail

echo "Testing Application CRD..."

echo
echo "1. Installing Application CRD"

kubectl apply \
  -f k8s/crds/applications.yaml \
  >/dev/null

echo "✓ Application CRD installed"


echo
echo "2. Testing valid Application"

kubectl apply \
  --dry-run=server \
  -f k8s/samples/application.yaml \
  >/dev/null

echo "✓ Valid Application accepted"


echo
echo "3. Testing invalid container port"

if kubectl apply --dry-run=server -f - >/dev/null 2>&1 <<'EOF'
apiVersion: platform.shubforge.dev/v1alpha1
kind: Application

metadata:
  name: invalid-port

spec:
  image: example:1.0

  replicas: 1

  port:
    containerPort: 70000
EOF
then
  echo "ERROR: Application with invalid port was accepted"
  exit 1
else
  echo "✓ Invalid port rejected"
fi


echo
echo "4. Testing missing image"

if kubectl apply --dry-run=server -f - >/dev/null 2>&1 <<'EOF'
apiVersion: platform.shubforge.dev/v1alpha1
kind: Application

metadata:
  name: missing-image

spec:
  replicas: 1

  port:
    containerPort: 8080
EOF
then
  echo "ERROR: Application without image was accepted"
  exit 1
else
  echo "✓ Missing image rejected"
fi


echo
echo "5. Testing replicas below minimum"

if kubectl apply --dry-run=server -f - >/dev/null 2>&1 <<'EOF'
apiVersion: platform.shubforge.dev/v1alpha1
kind: Application

metadata:
  name: invalid-replicas

spec:
  image: example:1.0

  replicas: 0

  port:
    containerPort: 8080
EOF
then
  echo "ERROR: Application with replicas=0 was accepted"
  exit 1
else
  echo "✓ Invalid replica count rejected"
fi


echo
echo "Application API tests passed."
