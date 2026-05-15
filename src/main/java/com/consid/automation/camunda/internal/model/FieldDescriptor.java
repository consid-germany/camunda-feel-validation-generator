package com.consid.automation.camunda.internal.model;

import java.util.List;
import java.util.Objects;

/**
 * Resolved description of a single OpenAPI field. Three orthogonal axes:
 * <ul>
 *   <li>{@code typeInfo} — the field's type and its type-specific constraints
 *       (length, range, items, allowed keys, etc.). Sealed; see {@link TypeInfo}.</li>
 *   <li>{@code nullable} — flips the rule from
 *       {@code field=null or (…)} to {@code field!=null and (…)}.</li>
 *   <li>{@code enumValues} — the allowed-value set; empty means no enum check.</li>
 *   <li>{@code dependsOn} — triggers that make this field conditionally required.
 *       Empty means unconditionally required.</li>
 * </ul>
 */
public record FieldDescriptor(TypeInfo typeInfo,
                              boolean nullable,
                              List<FeelLiteral> enumValues,
                              List<Trigger> dependsOn) {

    public FieldDescriptor {
        Objects.requireNonNull(typeInfo, "typeInfo");
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    public static FieldDescriptor of(TypeInfo typeInfo) {
        return new FieldDescriptor(typeInfo, false, List.of(), List.of());
    }

    public boolean hasEnum() {
        return !enumValues.isEmpty();
    }

    public boolean isConditional() {
        return !dependsOn.isEmpty();
    }

    /** Returns a copy of this descriptor with the given typeInfo, preserving all other axes. */
    public FieldDescriptor withTypeInfo(TypeInfo typeInfo) {
        return new FieldDescriptor(typeInfo, nullable, enumValues, dependsOn);
    }

    /** Returns a copy of this descriptor with the given dependsOn, preserving all other axes. */
    public FieldDescriptor withDependsOn(List<Trigger> dependsOn) {
        return new FieldDescriptor(typeInfo, nullable, enumValues, dependsOn);
    }
}
