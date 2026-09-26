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

        var status = greeting.getStatus();

        if (status == null) {
            status = new GreetingStatus();
        }

        status.setObservedGeneration(generation);

        status.setConfigMapName(
            configMap.getMetadata().getName()
        );

        status.setConditions(
            List.of(
                createCondition(
                    status,
                    generation,
                    "True",
                    "ConfigMapReady",
                    "Managed ConfigMap "
                        + configMap.getMetadata().getName()
                        + " is in the desired state"
                )
            )
        );

        greeting.setStatus(status);

        context.eventRecorder().normal(
            "Reconciled",
            "Managed ConfigMap "
                + configMap.getMetadata().getName()
                + " is in the desired state"
        );

        log.info(
            "Reconciled Greeting {}/{}",
            greeting.getMetadata().getNamespace(),
            greeting.getMetadata().getName()
        );

        return UpdateControl.patchStatus(greeting);
    }

    private Condition createCondition(
        GreetingStatus currentStatus,
        Long generation,
        String conditionStatus,
        String reason,
        String message) {

        var existingConditions =
            currentStatus == null
                || currentStatus.getConditions() == null
                ? List.<Condition>of()
                : currentStatus.getConditions();

        var previousReady =
            existingConditions.stream()
                .filter(condition ->
                    "Ready".equals(condition.getType()))
                .findFirst();

        var previousStatus =
            previousReady
                .map(Condition::getStatus)
                .orElse(null);

        var lastTransitionTime =
            previousReady.isPresent()
                && conditionStatus.equals(previousStatus)
                ? previousReady.get().getLastTransitionTime()
                : OffsetDateTime
                .now(ZoneOffset.UTC)
                .toString();

        return new ConditionBuilder()
            .withType("Ready")
            .withStatus(conditionStatus)
            .withObservedGeneration(generation)
            .withReason(reason)
            .withMessage(message)
            .withLastTransitionTime(lastTransitionTime)
            .build();
    }

    @Override
    public ErrorStatusUpdateControl<Greeting> updateErrorStatus(
        Greeting greeting,
        Context<Greeting> context,
        Exception exception) {

        var generation =
            greeting.getMetadata().getGeneration();

        var status = greeting.getStatus();

        if (status == null) {
            status = new GreetingStatus();
        }

        status.setObservedGeneration(generation);

        var message = errorMessage(exception);

        status.setConditions(
            List.of(
                createCondition(
                    status,
                    generation,
                    "False",
                    "ReconciliationFailed",
                    message
                )
            )
        );

        greeting.setStatus(status);

        context.eventRecorder().warn(
            "ReconciliationFailed",
            message
        );

        log.error(
            "Failed to reconcile Greeting {}/{}",
            greeting.getMetadata().getNamespace(),
            greeting.getMetadata().getName(),
            exception
        );

        return ErrorStatusUpdateControl.patchStatus(greeting);
    }

    private String errorMessage(Exception exception) {

        var message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return "Greeting reconciliation failed";
        }

        return message.length() > 500
            ? message.substring(0, 500)
            : message;
    }
}
