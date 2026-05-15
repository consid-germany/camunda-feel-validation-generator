package com.consid.automation.camunda.internal.feel;

import com.consid.automation.camunda.internal.model.*;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over how validation rules are generated and rendered for output.
 * Keeps the main generator decoupled from FEEL-specific concerns.
 */
public interface ValidationRuleBuilder {

    /**
     * Create a validation rule for the given field path and descriptor.
     */
    ValidationRule createRule(String fieldPath, FieldDescriptor descriptor);

    /**
     * Create a rule that enforces the root payload's
     * {@code additionalProperties: false} closure. Emits a separate rule
     * (not a per-field one) because the root has no parent property to attach to.
     */
    ValidationRule createRootObjectRule(ObjectTypeInfo rootClosure);

    /**
     * Render the grouped validation rules into the final FEEL output.
     */
    String render(Map<String, List<ValidationRule>> rulesByEndpoint);
}
