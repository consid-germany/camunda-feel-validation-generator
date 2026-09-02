package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.model.ObjectTypeInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output of {@link RequiredFieldsExtractor#extract}: the required fields found in
 * a request-body schema plus, kept separate, the root schema's
 * {@code additionalProperties: false} closure.
 *
 * <ul>
 *   <li>{@code requiredFields} — path → descriptor for every required field, in
 *       emission order.</li>
 *   <li>{@code rootClosure} — the closed set of allowed top-level keys, or null
 *       when the root is open.</li>
 * </ul>
 *
 * <p>The rule generator turns required fields into per-field rules and the root
 * closure into one extra "no unexpected top-level keys" rule.
 */
public record ExtractionResult(Map<String, FieldDescriptor> requiredFields,
                               ObjectTypeInfo rootClosure) {

    public ExtractionResult {
        // Preserve iteration order — downstream rule rendering depends on it.
        requiredFields = Collections.unmodifiableMap(new LinkedHashMap<>(requiredFields));
    }

    public boolean hasRootClosure() {
        return rootClosure != null;
    }
}
