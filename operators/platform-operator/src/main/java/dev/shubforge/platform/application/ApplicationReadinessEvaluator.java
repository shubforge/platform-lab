package dev.shubforge.platform.application;

import io.fabric8.kubernetes.api.model.apps.Deployment;

public final class ApplicationReadinessEvaluator {

    public ApplicationReadiness evaluate(Deployment deployment) {

        if (deployment == null) {
            return new ApplicationReadiness(
                0,
                false,
                "DeploymentPending",
                "Deployment has not been observed yet"
            );
        }

        var desiredReplicas =
            deployment.getSpec() != null
                && deployment.getSpec().getReplicas() != null
                ? deployment.getSpec().getReplicas()
                : 1;

        var status = deployment.getStatus();

        if (status == null) {
            return progressing(
                0,
                desiredReplicas
            );
        }

        var readyReplicas =
            value(status.getReadyReplicas());

        var availableReplicas =
            value(status.getAvailableReplicas());

        var updatedReplicas =
            value(status.getUpdatedReplicas());

        var deploymentGeneration =
            deployment.getMetadata().getGeneration() != null
                ? deployment.getMetadata().getGeneration()
                : 0L;

        var observedGeneration =
            status.getObservedGeneration() != null
                ? status.getObservedGeneration()
                : 0L;

        var latestGenerationObserved =
            observedGeneration >= deploymentGeneration;

        var ready =
            latestGenerationObserved
                && readyReplicas == desiredReplicas
                && availableReplicas == desiredReplicas
                && updatedReplicas == desiredReplicas;

        if (ready) {
            return new ApplicationReadiness(
                readyReplicas,
                true,
                "DeploymentReady",
                "Deployment has %d/%d ready replicas"
                    .formatted(
                        readyReplicas,
                        desiredReplicas
                    )
            );
        }

        return progressing(
            readyReplicas,
            desiredReplicas
        );
    }

    private ApplicationReadiness progressing(
        int readyReplicas,
        int desiredReplicas) {

        return new ApplicationReadiness(
            readyReplicas,
            false,
            "DeploymentProgressing",
            "Deployment has %d/%d ready replicas"
                .formatted(
                    readyReplicas,
                    desiredReplicas
                )
        );
    }

    private int value(Integer value) {
        return value != null ? value : 0;
    }
}
