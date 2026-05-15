package com.consid.automation.camunda.internal.model;

import java.math.BigDecimal;
import java.util.Objects;

public record FeelNumber(BigDecimal value) implements FeelLiteral {

    public FeelNumber {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String render() {
        // toPlainString avoids scientific notation (BigDecimal "1E+2" → "100") which FEEL won't parse.
        return value.toPlainString();
    }
}
