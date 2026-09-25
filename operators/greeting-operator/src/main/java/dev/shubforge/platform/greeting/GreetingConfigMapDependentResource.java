package dev.shubforge.platform.greeting;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class GreetingConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, Greeting> {

    public GreetingConfigMapDependentResource() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(
        Greeting greeting,
        Context<Greeting> context) {

        var name = greeting.getMetadata().getName();
        var namespace = greeting.getMetadata().getNamespace();

        return new ConfigMapBuilder()
            .withNewMetadata()
            .withName(name + "-greeting")
            .withNamespace(namespace)
            .addToLabels(
                "app.kubernetes.io/managed-by",
                "greeting-operator")
            .endMetadata()
            .addToData(
                "message",
                greeting.getSpec().getMessage())
            .build();
    }
}
