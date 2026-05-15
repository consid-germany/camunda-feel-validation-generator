package com.consid.automation.camunda.internal.model;

/**
 * String-family type with optional length / pattern constraints.
 *
 * <p>{@code format} selects the FEEL primitive used for the type check:
 * {@code PLAIN} → {@code instance of string}; {@code DATE} → {@code date(X)=null}; etc.
 * Length and pattern checks layer on top regardless of format.
 *
 * <p>{@code minLength: 0} stays nullable-coded (preserved as the explicit
 * "may be empty" signal so the builder skips the redundant lower bound).
 */
public record StringTypeInfo(StringFormat format,
                             Integer minLength,
                             Integer maxLength,
                             String pattern) implements TypeInfo {

    public enum StringFormat { PLAIN, DATE, DATE_TIME, TIME }

    public static final StringTypeInfo PLAIN = new StringTypeInfo(StringFormat.PLAIN, null, null, null);

    public static StringTypeInfo of(StringFormat format) {
        return new StringTypeInfo(format, null, null, null);
    }

    public boolean hasMinLength() {
        return minLength != null && minLength > 0;
    }

    public boolean hasMaxLength() {
        return maxLength != null;
    }

    public boolean hasPattern() {
        return pattern != null && !pattern.isEmpty();
    }
}
