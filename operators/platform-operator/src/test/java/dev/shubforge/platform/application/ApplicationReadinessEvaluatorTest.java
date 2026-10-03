package dev.shubforge.platform.application;

import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationReadinessEvaluatorTest {

    private final ApplicationReadinessEvaluator evaluator =
        new ApplicationReadinessEvaluator();

    @Test
    void shouldBeReadyWhenAllReplicasAreReady() {

        var deployment =
            new DeploymentBuilder()

                .withNewMetadata()
                .withGeneration(2L)
                .endMetadata()

                .withNewSpec()
                .withReplicas(3)
                .endSpec()

                .withNewStatus()
                .withObservedGeneration(2L)
                .withReadyReplicas(3)
                .withAvailableReplicas(3)
                .withUpdatedReplicas(3)
                .endStatus()

                .build();

        var result =
            evaluator.evaluate(deployment);

        assertThat(result.ready())
            .isTrue();

        assertThat(result.readyReplicas())
            .isEqualTo(3);

        assertThat(result.reason())
            .isEqualTo("DeploymentReady");
    }

    @Test
    void shouldBeProgressingWhenReplicasAreNotReady() {

        var deployment =
            new DeploymentBuilder()

                .withNewMetadata()
                .withGeneration(2L)
                .endMetadata()

                .withNewSpec()
                .withReplicas(3)
                .endSpec()

                .withNewStatus()
                .withObservedGeneration(2L)
                .withReadyReplicas(1)
                .withAvailableReplicas(1)
                .withUpdatedReplicas(3)
                .endStatus()

                .build();

        var result =
            evaluator.evaluate(deployment);

        assertThat(result.ready())
            .isFalse();

        assertThat(result.readyReplicas())
            .isEqualTo(1);

        assertThat(result.reason())
            .isEqualTo(
                "DeploymentProgressing"
            );
    }

    @Test
    void shouldNotBeReadyWhenLatestDeploymentGenerationIsNotObserved() {

        var deployment =
            new DeploymentBuilder()

                .withNewMetadata()
                .withGeneration(3L)
                .endMetadata()

                .withNewSpec()
                .withReplicas(2)
                .endSpec()

                .withNewStatus()
                .withObservedGeneration(2L)
                .withReadyReplicas(2)
                .withAvailableReplicas(2)
                .withUpdatedReplicas(2)
                .endStatus()

                .build();

        var result =
            evaluator.evaluate(deployment);

        assertThat(result.ready())
            .isFalse();

        assertThat(result.reason())
            .isEqualTo(
                "DeploymentProgressing"
            );
    }
}
