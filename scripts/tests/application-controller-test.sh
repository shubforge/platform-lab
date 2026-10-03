#!/usr/bin/env bash

set -euo pipefail

TEST_NAMESPACE="platform-test-$$"
APPLICATION_NAME="test-application"

cleanup() {
  echo
  echo "Cleaning up test namespace..."
  kubectl delete namespace \
    "${TEST_NAMESPACE}" \
    --ignore-not-found \
    >/dev/null
}

trap cleanup EXIT

echo "Testing Application Controller..."

echo
echo "1. Creating test namespace"

kubectl create namespace \
  "${TEST_NAMESPACE}" \
  >/dev/null

echo "✓ Namespace created"


echo
echo "2. Creating Application"

kubectl apply \
  --namespace "${TEST_NAMESPACE}" \
  -f - >/dev/null <<EOF
apiVersion: platform.shubforge.dev/v1alpha1
kind: Application

metadata:
  name: ${APPLICATION_NAME}

spec:
  image: example/test:1.0.0

  replicas: 2

  port:
    containerPort: 8080
EOF

echo "✓ Application created"


echo
echo "3. Waiting for Deployment"

for attempt in {1..30}; do

  if kubectl get deployment \
      "${APPLICATION_NAME}" \
      --namespace "${TEST_NAMESPACE}" \
      >/dev/null 2>&1; then

    break
  fi

  sleep 1
done

kubectl get deployment \
  "${APPLICATION_NAME}" \
  --namespace "${TEST_NAMESPACE}" \
  >/dev/null

echo "✓ Deployment created"


echo
echo "4. Verifying Deployment"

REPLICAS="$(
  kubectl get deployment \
    "${APPLICATION_NAME}" \
    --namespace "${TEST_NAMESPACE}" \
    -o jsonpath='{.spec.replicas}'
)"

IMAGE="$(
  kubectl get deployment \
    "${APPLICATION_NAME}" \
    --namespace "${TEST_NAMESPACE}" \
    -o jsonpath='{.spec.template.spec.containers[0].image}'
)"

PORT="$(
  kubectl get deployment \
    "${APPLICATION_NAME}" \
    --namespace "${TEST_NAMESPACE}" \
    -o jsonpath='{.spec.template.spec.containers[0].ports[0].containerPort}'
)"

test "${REPLICAS}" = "2"
test "${IMAGE}" = "example/test:1.0.0"
test "${PORT}" = "8080"

echo "✓ Deployment matches Application spec"


echo
echo "5. Waiting for Service"

for attempt in {1..30}; do

  if kubectl get service \
      "${APPLICATION_NAME}" \
      --namespace "${TEST_NAMESPACE}" \
      >/dev/null 2>&1; then

    break
  fi

  sleep 1
done

kubectl get service \
  "${APPLICATION_NAME}" \
  --namespace "${TEST_NAMESPACE}" \
  >/dev/null

echo "✓ Service created"


echo
echo "6. Verifying Service"

SERVICE_PORT="$(
  kubectl get service \
    "${APPLICATION_NAME}" \
    --namespace "${TEST_NAMESPACE}" \
    -o jsonpath='{.spec.ports[0].port}'
)"

TARGET_PORT="$(
  kubectl get service \
    "${APPLICATION_NAME}" \
    --namespace "${TEST_NAMESPACE}" \
    -o jsonpath='{.spec.ports[0].targetPort}'
)"

test "${SERVICE_PORT}" = "8080"
test "${TARGET_PORT}" = "8080"

echo "✓ Service matches Application spec"


echo
echo "7. Verifying Application status"

DEPLOYMENT_NAME="$(
  kubectl get application \
    "${APPLICATION_NAME}" \
    --namespace "${TEST_NAMESPACE}" \
    -o jsonpath='{.status.deploymentName}'
)"

SERVICE_NAME="$(
  kubectl get application \
    "${APPLICATION_NAME}" \
    --namespace "${TEST_NAMESPACE}" \
    -o jsonpath='{.status.serviceName}'
)"

test "${DEPLOYMENT_NAME}" = "${APPLICATION_NAME}"
test "${SERVICE_NAME}" = "${APPLICATION_NAME}"

echo "✓ Application status updated"


echo
echo "Application Controller tests passed."
