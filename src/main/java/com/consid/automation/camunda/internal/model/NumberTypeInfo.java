package com.consid.automation.camunda.internal.model;

import java.math.BigDecimal;

/**
 * Number-family type with optional range and divisibility constraints.
 *
 * <p>Lower and upper bounds carry both inclusive ({@code minimum} /
 * {@code maximum}) and exclusive ({@code exclusiveMinimum} /
 * {@code exclusiveMaximum}) slots; at most one of each pair is populated.
 * The resolver normalizes the OpenAPI 3.0 boolean form into the 3.1 numeric
 * form so the builder sees a single representation.
 */
public record NumberTypeInfo(BigDecimal minimum,
                             BigDecimal exclusiveMinimum,
                             BigDecimal maximum,
                             BigDecimal exclusiveMaximum,
                             BigDecimal multipleOf) implements TypeInfo {

    public static final NumberTypeInfo NONE = new NumberTypeInfo(null, null, null, null, null);

    public boolean hasMinimum() {
        return minimum != null;
    }

    public boolean hasExclusiveMinimum() {
        return exclusiveMinimum != null;
    }

    public boolean hasMaximum() {
        return maximum != null;
    }

    public boolean hasExclusiveMaximum() {
        return exclusiveMaximum != null;
    }

    public boolean hasMultipleOf() {
        return multipleOf != null;
    }
}
