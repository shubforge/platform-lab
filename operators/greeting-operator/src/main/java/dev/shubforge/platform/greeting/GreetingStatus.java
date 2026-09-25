package dev.shubforge.platform.greeting;

import io.fabric8.kubernetes.api.model.Condition;

import java.util.ArrayList;
import java.util.List;

public class GreetingStatus {

    private Long observedGeneration;

    private String configMapName;

    private List<Condition> conditions = new ArrayList<>();

    public Long getObservedGeneration() {
        return observedGeneration;
    }

    public void setObservedGeneration(Long observedGeneration) {
        this.observedGeneration = observedGeneration;
    }

    public String getConfigMapName() {
        return configMapName;
    }

    public void setConfigMapName(String configMapName) {
        this.configMapName = configMapName;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }
}
