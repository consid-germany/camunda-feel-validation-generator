package com.consid.automation.camunda.internal.model;

import java.util.Objects;

/**
 * One rendered rule: a FEEL expression that is {@code true} when the input is
 * invalid, plus the identifiers the response mode prints next to it.
 *
 * @param id               stable identifier, e.g. {@code email-invalid} or {@code params.tenant-invalid}
 * @param invalidExpression FEEL violation expression
 * @param fieldPath        dotted path shown to the caller ({@code (root)} for the closed-root rule)
 * @param source           which part of the webhook request the rule reads
 */
public record ValidationRule(String id, String invalidExpression, String fieldPath, InputSource source) {

    /** The request part a rule reads; decides whether the body alias is bound in the output. */
    public enum InputSource { BODY, QUERY }

    public ValidationRule {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(invalidExpression, "invalidExpression must not be null");
        Objects.requireNonNull(fieldPath, "fieldPath must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }
}
