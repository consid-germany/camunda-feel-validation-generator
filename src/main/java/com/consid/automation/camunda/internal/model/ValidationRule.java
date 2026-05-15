package com.consid.automation.camunda.internal.model;

import java.util.Objects;

/**
 * Immutable representation of a single validation rule.
 */
public record ValidationRule(String id, String invalidExpression, String fieldPath) {

    public ValidationRule {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(invalidExpression, "invalidExpression must not be null");
        Objects.requireNonNull(fieldPath, "fieldPath must not be null");
    }

    public static ValidationRule create(String id, String invalidExpression, String fieldPath) {
        return new ValidationRule(id, invalidExpression, fieldPath);
    }
}
