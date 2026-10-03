package dev.shubforge.platform.application;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        var status =
            application.getStatus();

        if (status == null) {
            status = new ApplicationStatus();
        }

        status.setObservedGeneration(
            application.getMetadata().getGeneration()
        );

        status.setDeploymentName(name);
        status.setServiceName(name);

        application.setStatus(status);

        return UpdateControl.patchStatus(application);
    }
}
