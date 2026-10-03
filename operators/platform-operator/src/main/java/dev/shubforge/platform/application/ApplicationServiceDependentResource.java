package dev.shubforge.platform.application;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import java.util.Map;

@KubernetesDependent
public class ApplicationServiceDependentResource
    extends CRUDKubernetesDependentResource<
    Service,
    Application> {

    private static final String APPLICATION_LABEL =
        "platform.shubforge.dev/application";

    @Override
    protected Service desired(
        Application application,
        Context<Application> context) {

        var name =
            application.getMetadata().getName();

        var namespace =
            application.getMetadata().getNamespace();

        var containerPort =
            application.getSpec()
                .getPort()
                .getContainerPort();

        Map<String, String> selector =
            Map.of(
                APPLICATION_LABEL,
                name
            );

        return new ServiceBuilder()

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

            .withSelector(selector)

            .addNewPort()
            .withName("http")
            .withPort(containerPort)
            .withNewTargetPort(containerPort)
            .withProtocol("TCP")
            .endPort()

            .endSpec()

            .build();
    }
}
