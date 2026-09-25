package dev.shubforge.platform.greeting;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Workflow(
    dependents = {
        @Dependent(
            type = GreetingConfigMapDependentResource.class
        )
    }
)
@ControllerConfiguration
public class GreetingReconciler
    implements Reconciler<Greeting> {

    private static final Logger log =
        LoggerFactory.getLogger(GreetingReconciler.class);

    @Override
    public UpdateControl<Greeting> reconcile(
        Greeting greeting,
        Context<Greeting> context) {

        var configMap = context
            .getSecondaryResource(ConfigMap.class)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Managed ConfigMap not found"));

        var generation =
            greeting.getMetadata().getGeneration();

        var previousStatus =
            greeting.getStatus();

        var status = new GreetingStatus();

        status.setObservedGeneration(generation);

        status.setConfigMapName(
            configMap.getMetadata().getName()
        );

        status.setConditions(
            List.of(
                createReadyCondition(
                    previousStatus,
                    generation,
                    configMap.getMetadata().getName()
                )
            )
        );

        greeting.setStatus(status);

        log.info(
            "Reconciled Greeting {}/{} -> {}",
            greeting.getMetadata().getNamespace(),
            greeting.getMetadata().getName(),
            configMap.getMetadata().getName()
        );

        return UpdateControl.patchStatus(greeting);
    }

    private Condition createReadyCondition(
        GreetingStatus previousStatus,
        Long generation,
        String configMapName) {

        var previousConditions =
            previousStatus == null
                || previousStatus.getConditions() == null
                ? List.<Condition>of()
                : previousStatus.getConditions();

        Optional<Condition> previousReady =
            previousConditions.stream()
                .filter(condition ->
                    "Ready".equals(condition.getType()))
                .findFirst();

        var lastTransitionTime =
            previousReady
                .filter(condition ->
                    "True".equals(condition.getStatus()))
                .map(Condition::getLastTransitionTime)
                .orElseGet(() ->
                    OffsetDateTime
                        .now(ZoneOffset.UTC)
                        .toString());

        return new ConditionBuilder()
            .withType("Ready")
            .withStatus("True")
            .withObservedGeneration(generation)
            .withReason("ConfigMapReady")
            .withMessage(
                "Managed ConfigMap "
                    + configMapName
                    + " is in the desired state"
            )
            .withLastTransitionTime(lastTransitionTime)
            .build();
    }
}
