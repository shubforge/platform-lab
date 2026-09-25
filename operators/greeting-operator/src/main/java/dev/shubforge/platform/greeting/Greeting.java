package dev.shubforge.platform.greeting;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("platform.shubforge.dev")
@Version("v1alpha1")
@Kind("Greeting")
@Plural("greetings")
public class Greeting
    extends CustomResource<GreetingSpec, GreetingStatus>
    implements Namespaced {
}
