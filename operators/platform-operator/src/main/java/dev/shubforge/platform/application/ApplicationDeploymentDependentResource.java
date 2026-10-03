package dev.shubforge.platform.application;

import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

@KubernetesDependent
public class ApplicationDeploymentDependentResource
    extends CRUDKubernetesDependentResource<
    Deployment,
    Application> {

    private static final String APPLICATION_LABEL =
        "platform.shubforge.dev/application";

    @Override
    protected Deployment desired(
        Application application,
        Context<Application> context) {

        var name =
            application.getMetadata().getName();

        var namespace =
            application.getMetadata().getNamespace();

        var spec =
            application.getSpec();

        Map<String, String> labels =
            Map.of(
                APPLICATION_LABEL,
                name
            );

        return new DeploymentBuilder()

            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .addToLabels(
                "app.kubernetes.io/managed-by",
                "platform-operator"
            )
            .addToLabels(
                APPLICATION_LABEL,
                name
            )
            .endMetadata()

            .withNewSpec()

            .withReplicas(spec.getReplicas())

            .withNewSelector()
            .withMatchLabels(labels)
            .endSelector()

            .withTemplate(
                new PodTemplateSpecBuilder()

                    .withNewMetadata()
                    .withLabels(labels)
                    .endMetadata()

                    .withSpec(
                        new PodSpecBuilder()

                            .addNewContainer()
                            .withName("application")
                            .withImage(spec.getImage())

                            .withPorts(
                                new ContainerPortBuilder()
                                    .withContainerPort(
                                        spec.getPort()
                                            .getContainerPort()
                                    )
                                    .build()
                            )

                            .endContainer()

                            .build()
                    )

                    .build()
            )

            .endSpec()

            .build();
    }
}
