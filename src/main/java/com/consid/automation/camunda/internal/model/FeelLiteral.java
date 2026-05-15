package com.consid.automation.camunda.internal.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed FEEL literal value used in enum membership checks and value-trigger
 * comparisons. Each subtype owns its own rendering (escaping for strings,
 * {@link BigDecimal#toPlainString()} for numbers) so the expression builder
 * never has to inspect a raw {@code Object} again.
 */
public sealed interface FeelLiteral permits FeelString, FeelNumber, FeelBoolean, FeelNull {

    String render();

    /** Wrap an unknown Object as the appropriate literal. */
    static FeelLiteral of(Object value) {
        if (value == null) {
            return new FeelNull();
        }
        if (value instanceof Boolean b) {
            return new FeelBoolean(b);
        }
        if (value instanceof BigDecimal bd) {
            return new FeelNumber(bd);
        }
        if (value instanceof Number n) {
            return new FeelNumber(new BigDecimal(n.toString()));
        }
        return new FeelString(value.toString());
    }

    /** Convenience for converting an existing list of raw Objects. */
    static List<FeelLiteral> listOf(List<?> values) {
        List<FeelLiteral> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(of(value));
        }
        return List.copyOf(result);
    }
}
