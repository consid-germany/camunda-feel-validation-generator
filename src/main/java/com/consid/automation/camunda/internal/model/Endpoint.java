package com.consid.automation.camunda.internal.model;

import java.util.Objects;

/**
 * An HTTP operation the generator emits rules for: the upper-case method and
 * the OpenAPI path template. Used as the grouping key between scanning and
 * rendering so neither side has to know how the other spells a heading.
 */
public record Endpoint(String method, String path) {

    public Endpoint {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
    }

    @Override
    public String toString() {
        return method + " " + path;
    }
}
