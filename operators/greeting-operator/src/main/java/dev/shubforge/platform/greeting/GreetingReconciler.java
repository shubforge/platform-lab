package dev.shubforge.platform.greeting;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        log.info(
            "Reconciled Greeting {}/{}",
            greeting.getMetadata().getNamespace(),
            greeting.getMetadata().getName()
        );

        return UpdateControl.noUpdate();
    }
}
