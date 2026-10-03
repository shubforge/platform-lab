package dev.shubforge.platform.application;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("platform.shubforge.dev")
@Version("v1alpha1")
@Kind("Application")
@Plural("applications")
public class Application
    extends CustomResource<ApplicationSpec, ApplicationStatus>
    implements Namespaced {
}
