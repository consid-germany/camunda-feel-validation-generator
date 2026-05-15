package com.consid.automation.camunda.internal.model;

import java.util.Set;

/**
 * Object-family type with optional {@code additionalProperties: false}
 * closed-key set. {@link #allowedKeys()} is null when the object is open
 * (the default); non-null when the schema lists a closed set of property names.
 */
public record ObjectTypeInfo(Set<String> allowedKeys) implements TypeInfo {

    public static final ObjectTypeInfo OPEN = new ObjectTypeInfo(null);

    public ObjectTypeInfo {
        allowedKeys = allowedKeys == null ? null : Set.copyOf(allowedKeys);
    }

    public boolean isClosed() {
        return allowedKeys != null;
    }
}
