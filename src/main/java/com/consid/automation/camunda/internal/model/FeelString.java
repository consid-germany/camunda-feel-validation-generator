package com.consid.automation.camunda.internal.model;

import java.util.Objects;

public record FeelString(String value) implements FeelLiteral {

    public FeelString {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String render() {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
