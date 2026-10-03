package dev.shubforge.platform.application;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Workflow(
    dependents = {
        @Dependent(
            type = ApplicationDeploymentDependentResource.class
        ),
        @Dependent(
            type = ApplicationServiceDependentResource.class
        )
    }
)
@ControllerConfiguration
public class ApplicationReconciler
    implements Reconciler<Application> {

    private static final Logger log =
        LoggerFactory.getLogger(
            ApplicationReconciler.class
        );

    private final ApplicationReadinessEvaluator readinessEvaluator =
        new ApplicationReadinessEvaluator();

    @Override
    public UpdateControl<Application> reconcile(
        Application application,
        Context<Application> context) {

        var namespace =
            application.getMetadata().getNamespace();

        var name =
            application.getMetadata().getName();

        log.info(
            "Reconciling Application {}/{}",
            namespace,
            name
        );

        var status = application.getStatus();

        if (status == null) {
            status = new ApplicationStatus();
        }

        var generation =
            application.getMetadata().getGeneration();

        status.setObservedGeneration(generation);
        status.setDeploymentName(name);
        status.setServiceName(name);

        var deployment =
            context.getSecondaryResource(
                Deployment.class
            ).orElse(null);

        var readiness =
            readinessEvaluator.evaluate(deployment);

        status.setReadyReplicas(
            readiness.readyReplicas()
        );

        status.setConditions(
            List.of(
                readyCondition(
                    status,
                    generation,
                    readiness
                )
            )
        );

        application.setStatus(status);

        return UpdateControl.patchStatus(application);
    }

    private Condition readyCondition(
        ApplicationStatus currentStatus,
        Long generation,
        ApplicationReadiness readiness) {

        var conditionStatus =
            readiness.ready()
                ? "True"
                : "False";

        var previous =
            currentStatus.getConditions() == null
                ? null
                : currentStatus.getConditions()
                .stream()
                .filter(condition ->
                    "Ready".equals(
                        condition.getType()
                    )
                )
                .findFirst()
                .orElse(null);

        String transitionTime;

        if (previous != null
            && conditionStatus.equals(
            previous.getStatus()
        )) {

            transitionTime =
                previous.getLastTransitionTime();

        } else {

            transitionTime =
                OffsetDateTime.now(
                    ZoneOffset.UTC
                ).toString();
        }

        return new ConditionBuilder()
            .withType("Ready")
            .withStatus(conditionStatus)
            .withObservedGeneration(generation)
            .withReason(readiness.reason())
            .withMessage(readiness.message())
            .withLastTransitionTime(transitionTime)
            .build();
    }
}
