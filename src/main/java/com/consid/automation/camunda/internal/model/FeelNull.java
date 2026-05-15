package com.consid.automation.camunda.internal.model;

public record FeelNull() implements FeelLiteral {

    @Override
    public String render() {
        return "null";
    }
}
