package dev.shubforge.platform.greeting;

import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import java.util.Map;
import java.util.Objects;

public class GreetingUpdateFilter implements OnUpdateFilter<Greeting> {

    public static final String RECONCILE_AT_ANNOTATION =
        "platform.shubforge.dev/reconcile-at";

    @Override
    public boolean accept(
        Greeting newResource,
        Greeting oldResource) {

        return generationChanged(newResource, oldResource)
            || reconcileAnnotationChanged(newResource, oldResource);
    }

    private boolean generationChanged(
        Greeting newResource,
        Greeting oldResource) {

        return !Objects.equals(
            newResource.getMetadata().getGeneration(),
            oldResource.getMetadata().getGeneration()
        );
    }

    private boolean reconcileAnnotationChanged(
        Greeting newResource,
        Greeting oldResource) {

        var newValue = annotationValue(newResource);
        var oldValue = annotationValue(oldResource);

        return !Objects.equals(newValue, oldValue);
    }

    private String annotationValue(Greeting greeting) {

        Map<String, String> annotations =
            greeting.getMetadata().getAnnotations();

        if (annotations == null) {
            return null;
        }

        return annotations.get(RECONCILE_AT_ANNOTATION);
    }
}
