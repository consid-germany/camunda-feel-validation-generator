package com.consid.automation.camunda.internal.model;

import java.util.Objects;

public record PresenceTrigger(String path) implements Trigger {

    public PresenceTrigger {
        Objects.requireNonNull(path, "path");
    }

    @Override
    public Trigger withPrefix(String prefix) {
        return new PresenceTrigger(prefix + path);
    }
}
