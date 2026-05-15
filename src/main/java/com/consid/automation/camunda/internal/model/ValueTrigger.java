package com.consid.automation.camunda.internal.model;

import java.util.List;
import java.util.Objects;

public record ValueTrigger(String path, List<FeelLiteral> allowedValues) implements Trigger {

    public ValueTrigger {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(allowedValues, "allowedValues");
        if (allowedValues.isEmpty()) {
            throw new IllegalArgumentException("ValueTrigger must carry at least one allowed value");
        }
        allowedValues = List.copyOf(allowedValues);
    }

    @Override
    public Trigger withPrefix(String prefix) {
        return new ValueTrigger(prefix + path, allowedValues);
    }
}
