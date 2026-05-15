package com.consid.automation.camunda.internal.model;

import java.util.Map;

/**
 * Array-family type with optional sizing bounds and element schema.
 *
 * <p>{@code items} carries the element's type-level descriptor (null when
 * the array has no items schema or items don't resolve). When items are
 * object-typed, {@code itemRequiredFields} maps element-relative paths to the
 * descriptors needed to validate them; the builder folds these into a
 * {@code some e in X satisfies (...)} clause.
 */
public record ArrayTypeInfo(Integer minItems,
                            Integer maxItems,
                            FieldDescriptor items,
                            Map<String, FieldDescriptor> itemRequiredFields) implements TypeInfo {

    public static final ArrayTypeInfo NONE = new ArrayTypeInfo(null, null, null, Map.of());

    public ArrayTypeInfo {
        itemRequiredFields = itemRequiredFields == null ? Map.of() : Map.copyOf(itemRequiredFields);
    }

    public ArrayTypeInfo(Integer minItems, Integer maxItems) {
        this(minItems, maxItems, null, Map.of());
    }

    public boolean hasMinItems() {
        return minItems != null && minItems > 0;
    }

    public boolean hasMaxItems() {
        return maxItems != null;
    }

    public boolean hasItems() {
        return items != null;
    }
}
