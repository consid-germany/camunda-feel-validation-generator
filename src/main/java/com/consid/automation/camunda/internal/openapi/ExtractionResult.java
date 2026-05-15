package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.model.ObjectTypeInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output of {@link RequiredFieldsExtractor#extract}. Splits the two concerns
 * that used to share a magic empty-string map key:
 *
 * <ul>
 *   <li>{@code requiredFields} — path → descriptor for every required field
 *       discovered in the schema.</li>
 *   <li>{@code rootClosure} — non-null when the root schema declares
 *       {@code additionalProperties: false}, carrying the closed set of
 *       allowed top-level keys. Null otherwise.</li>
 * </ul>
 *
 * <p>The rule generator handles each separately: required fields become per-field
 * rules; the root closure becomes one extra "no unexpected top-level keys" rule.
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
