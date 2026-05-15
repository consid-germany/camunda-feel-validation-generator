package com.consid.automation.camunda.internal.model;

import java.util.List;

/**
 * A conditional-requirement trigger. A field is required when at least one
 * of its triggers fires.
 *
 * <ul>
 *   <li>{@link PresenceTrigger} fires when the referenced path is non-null.
 *       Used for {@code dependentRequired}.</li>
 *   <li>{@link ValueTrigger} fires when the referenced path equals one of the
 *       listed literals. Used for the supported subset of JSON Schema
 *       {@code if}/{@code then} (single-property predicates with {@code const}
 *       or {@code enum}) and for discriminator-aware {@code oneOf} branches.</li>
 * </ul>
 *
 * <p>{@code path} is a dot-path from the request body root. The FEEL renderer
 * prepends {@code req.} via {@link #withPrefix(String)} when emitting.
 */
public sealed interface Trigger permits PresenceTrigger, ValueTrigger {

    String path();

    /** Returns a copy of this trigger with the given prefix prepended to the path. */
    Trigger withPrefix(String prefix);

    static Trigger presence(String path) {
        return new PresenceTrigger(path);
    }

    static Trigger value(String path, List<FeelLiteral> allowedValues) {
        return new ValueTrigger(path, allowedValues);
    }
}
