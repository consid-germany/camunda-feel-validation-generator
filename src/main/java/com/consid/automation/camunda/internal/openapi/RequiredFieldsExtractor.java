package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.Diagnostics;
import com.consid.automation.camunda.internal.model.*;

import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks an OpenAPI schema and produces a path-keyed map of {@link FieldDescriptor}s
 * for everything the FEEL generator must enforce: direct required fields,
 * dependent-required dependents, if/then dependents, discriminated oneOf
 * branches, and nested-object inner required fields with trigger inheritance.
 */
public class RequiredFieldsExtractor {

    private final FieldTypeResolver typeResolver;
    private final Diagnostics diagnostics;

    public RequiredFieldsExtractor(FieldTypeResolver typeResolver) {
        this(typeResolver, Diagnostics.NOOP);
    }

    public RequiredFieldsExtractor(FieldTypeResolver typeResolver, Diagnostics diagnostics) {
        this.typeResolver = typeResolver;
        this.diagnostics = diagnostics;
    }

    public ExtractionResult extract(Schema<?> schema) {
        Map<String, FieldDescriptor> requiredFields = new LinkedHashMap<>();
        Set<Schema<?>> activeStack = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRequiredFields(schema, requiredFields, "", activeStack, List.of());
        return new ExtractionResult(requiredFields, rootClosureFor(schema));
    }

    /**
     * Reads the root schema's {@code additionalProperties: false} as an
     * {@link ObjectTypeInfo} closure, surfaced separately so the rule generator
     * can emit one extra "no unexpected top-level keys" rule. Nested objects
     * with the same keyword are handled by the regular descriptor-driven flow.
     */
    private ObjectTypeInfo rootClosureFor(Schema<?> root) {
        if (root == null) {
            return null;
        }
        FieldDescriptor rootDescriptor = typeResolver.resolve(root);
        if (rootDescriptor.typeInfo() instanceof ObjectTypeInfo object && object.isClosed()) {
            return object;
        }
        return null;
    }

    /**
     * Recursively collects required fields from a schema.
     * {@code activeStack} tracks schemas currently on the recursion path so a
     * self-referential schema terminates while a component reused at multiple
     * field paths is still expanded each time. {@code inheritedTriggers} carry
     * conditional triggers from an ancestor down into a nested object's required
     * fields.
     */
    private void collectRequiredFields(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                                       String pathPrefix, Set<Schema<?>> activeStack,
                                       List<Trigger> inheritedTriggers) {
        if (schema == null) {
            return;
        }
        schema = typeResolver.resolveSchemaReference(schema);
        if (schema == null) {
            return;
        }
        if (!activeStack.add(schema)) {
            return;
        }
        try {
            processDirectRequiredFields(schema, requiredFields, pathPrefix, inheritedTriggers);
            processDependentRequired(schema, requiredFields, pathPrefix, inheritedTriggers);
            processConditional(schema, requiredFields, pathPrefix, inheritedTriggers);
            processComposition(schema.getAllOf(), requiredFields, pathPrefix, activeStack, inheritedTriggers);
            processOneOf(schema, requiredFields, pathPrefix, activeStack, inheritedTriggers);
            processComposition(schema.getAnyOf(), requiredFields, pathPrefix, activeStack, inheritedTriggers);
            processNestedProperties(schema, requiredFields, pathPrefix, activeStack);
        } finally {
            activeStack.remove(schema);
        }
    }

    private void processDirectRequiredFields(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                                             String pathPrefix, List<Trigger> inheritedTriggers) {
        if (schema.getRequired() == null || schema.getProperties() == null) {
            return;
        }
        List<String> requiredFieldsList = new ArrayList<>(schema.getRequired());
        Collections.sort(requiredFieldsList);
        var properties = schema.getProperties();
        for (String requiredField : requiredFieldsList) {
            String fullFieldPath = buildFieldPath(pathPrefix, requiredField);
            if (requiredFields.containsKey(fullFieldPath)) {
                continue;
            }
            Schema<?> propertySchema = properties.get(requiredField);
            FieldDescriptor base = enrichArrayItems(typeResolver.resolve(propertySchema), propertySchema);
            FieldDescriptor descriptor = inheritedTriggers.isEmpty()
                ? base
                : base.withDependsOn(inheritedTriggers);
            requiredFields.put(fullFieldPath, descriptor);
        }
    }

    /**
     * Walks an array's {@code items} schema for required fields and attaches
     * them to the descriptor's {@link ArrayTypeInfo}. Without this, an array of
     * objects passes validation as long as the list is well-typed — the
     * element-level required-field checks never get emitted.
     */
    private FieldDescriptor enrichArrayItems(FieldDescriptor descriptor, Schema<?> propertySchema) {
        if (!(descriptor.typeInfo() instanceof ArrayTypeInfo array)) {
            return descriptor;
        }
        Schema<?> items = propertySchema == null ? null : propertySchema.getItems();
        if (items == null) {
            return descriptor;
        }
        Map<String, FieldDescriptor> itemRequired = extractItemRequiredFields(items);
        return descriptor.withTypeInfo(new ArrayTypeInfo(
            array.minItems(), array.maxItems(), array.items(), itemRequired));
    }

    private Map<String, FieldDescriptor> extractItemRequiredFields(Schema<?> itemsSchema) {
        Map<String, FieldDescriptor> inner = new LinkedHashMap<>();
        Set<Schema<?>> innerStack = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRequiredFields(itemsSchema, inner, "", innerStack, List.of());
        return inner;
    }

    @SuppressWarnings("rawtypes")
    private void processDependentRequired(Schema<?> schema,
                                          Map<String, FieldDescriptor> requiredFields,
                                          String pathPrefix,
                                          List<Trigger> inheritedTriggers) {
        Map<String, List<String>> dependentRequired = schema.getDependentRequired();
        if (dependentRequired == null || dependentRequired.isEmpty()) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        List<String> triggers = new ArrayList<>(dependentRequired.keySet());
        Collections.sort(triggers);
        for (String trigger : triggers) {
            Trigger presence = Trigger.presence(buildFieldPath(pathPrefix, trigger));
            List<String> dependents = new ArrayList<>(dependentRequired.get(trigger));
            Collections.sort(dependents);
            for (String dependent : dependents) {
                addConditional(properties, dependent, presence, pathPrefix, requiredFields, inheritedTriggers);
            }
        }
    }

    /**
     * Reads the supported subset of JSON Schema {@code if}/{@code then}: a
     * single-property predicate with {@code const} or {@code enum} plus
     * {@code required: [<that property>]}, and a {@code then} block listing
     * required field names. Shapes outside this subset are silently ignored.
     */
    @SuppressWarnings("rawtypes")
    private void processConditional(Schema<?> schema,
                                    Map<String, FieldDescriptor> requiredFields,
                                    String pathPrefix,
                                    List<Trigger> inheritedTriggers) {
        Schema<?> ifSchema = schema.getIf();
        Schema<?> thenSchema = schema.getThen();
        if (ifSchema == null || thenSchema == null) {
            return;
        }
        Trigger trigger = extractValueTrigger(ifSchema, pathPrefix);
        if (trigger == null) {
            diagnostics.warn(locationLabel(pathPrefix),
                "if/then predicate shape not supported and the conditional was skipped; "
                    + "only a single-property predicate using `const` or `enum` "
                    + "(plus `required: [<that property>]`) is honored");
            return;
        }
        List<String> thenRequired = thenSchema.getRequired();
        if (thenRequired == null || thenRequired.isEmpty()) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        List<String> dependents = new ArrayList<>(thenRequired);
        Collections.sort(dependents);
        for (String dependent : dependents) {
            addConditional(properties, dependent, trigger, pathPrefix, requiredFields, inheritedTriggers);
        }
    }

    /** Pulls a value trigger out of a supported {@code if} subschema, or null when the shape isn't handled. */
    @SuppressWarnings("rawtypes")
    private Trigger extractValueTrigger(Schema<?> ifSchema, String pathPrefix) {
        Map<String, Schema> ifProperties = ifSchema.getProperties();
        List<String> ifRequired = ifSchema.getRequired();
        if (ifProperties == null || ifProperties.size() != 1
            || ifRequired == null || ifRequired.size() != 1) {
            return null;
        }
        String triggerProperty = ifProperties.keySet().iterator().next();
        if (!triggerProperty.equals(ifRequired.get(0))) {
            return null;
        }
        Schema<?> predicate = ifProperties.get(triggerProperty);
        List<FeelLiteral> allowedValues = literalValues(predicate);
        if (allowedValues.isEmpty()) {
            return null;
        }
        return Trigger.value(buildFieldPath(pathPrefix, triggerProperty), allowedValues);
    }

    private List<FeelLiteral> literalValues(Schema<?> predicate) {
        if (predicate == null) {
            return List.of();
        }
        if (predicate.getConst() != null) {
            return List.of(FeelLiteral.of(predicate.getConst()));
        }
        if (predicate.getEnum() != null && !predicate.getEnum().isEmpty()) {
            return FeelLiteral.listOf(predicate.getEnum());
        }
        return List.of();
    }

    @SuppressWarnings("rawtypes")
    private void addConditional(Map<String, Schema> properties,
                                String fieldName,
                                Trigger trigger,
                                String pathPrefix,
                                Map<String, FieldDescriptor> requiredFields,
                                List<Trigger> inheritedTriggers) {
        String fieldPath = buildFieldPath(pathPrefix, fieldName);
        FieldDescriptor existing = requiredFields.get(fieldPath);
        if (existing != null && !existing.isConditional()) {
            // Already unconditionally required — the stricter constraint wins.
            return;
        }
        if (existing != null) {
            // Multiple triggers for the same field: OR-merge them.
            if (existing.dependsOn().contains(trigger)) {
                return;
            }
            List<Trigger> merged = new ArrayList<>(existing.dependsOn());
            merged.add(trigger);
            requiredFields.put(fieldPath, existing.withDependsOn(merged));
            return;
        }
        Schema<?> propertySchema = properties.get(fieldName);
        FieldDescriptor base = enrichArrayItems(typeResolver.resolve(propertySchema), propertySchema);
        List<Trigger> dependsOn = new ArrayList<>(inheritedTriggers);
        dependsOn.add(trigger);
        requiredFields.put(fieldPath, base.withDependsOn(dependsOn));
    }

    /**
     * Handles {@code oneOf}: with a {@link Discriminator} + explicit mapping
     * each branch's required fields become conditional on the discriminator
     * value, and the discriminator property itself is pinned to the enum of
     * mapping keys as an unconditional required field. Without a mapping,
     * falls back to union-merge so existing fixtures keep working.
     */
    private void processOneOf(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                              String pathPrefix, Set<Schema<?>> activeStack,
                              List<Trigger> inheritedTriggers) {
        List<?> oneOf = schema.getOneOf();
        if (oneOf == null || oneOf.isEmpty()) {
            return;
        }
        Discriminator discriminator = schema.getDiscriminator();
        Map<String, String> mapping = discriminator == null ? null : discriminator.getMapping();
        String propertyName = discriminator == null ? null : discriminator.getPropertyName();
        if (propertyName == null || mapping == null || mapping.isEmpty()) {
            diagnostics.warn(locationLabel(pathPrefix),
                "oneOf without `discriminator.mapping` falls back to union-merge "
                    + "(all branches' required fields are accumulated, which is stricter than the spec implies); "
                    + "add a `discriminator` with explicit `mapping` to scope branch fields to their type value");
            processComposition(oneOf, requiredFields, pathPrefix, activeStack, inheritedTriggers);
            return;
        }
        String discriminatorPath = buildFieldPath(pathPrefix, propertyName);
        addDiscriminatorAsRequired(discriminatorPath, mapping.keySet(), requiredFields, inheritedTriggers);

        // Reverse-lookup: $ref → discriminator value, so each branch can find its trigger.
        Map<String, String> refToValue = new HashMap<>();
        mapping.forEach((value, ref) -> refToValue.put(ref, value));

        for (Object element : oneOf) {
            if (!(element instanceof Schema<?> branch)) {
                continue;
            }
            String ref = branch.get$ref();
            String discriminatorValue = ref == null ? null : refToValue.get(ref);
            if (discriminatorValue == null) {
                // Branch not in mapping — union-merge fallback for that branch alone.
                collectRequiredFields(branch, requiredFields, pathPrefix, activeStack, inheritedTriggers);
            } else {
                Trigger branchTrigger = Trigger.value(
                    discriminatorPath, List.of(new FeelString(discriminatorValue)));
                List<Trigger> branchTriggers = new ArrayList<>(inheritedTriggers);
                branchTriggers.add(branchTrigger);
                collectRequiredFields(branch, requiredFields, pathPrefix, activeStack, branchTriggers);
            }
        }
    }

    /**
     * Pins the discriminator property as an unconditionally required STRING
     * whose enum is the set of mapping keys. Without this the discriminator
     * would only appear conditionally on its own value — a missing property
     * would silently disable all branch checks.
     */
    private void addDiscriminatorAsRequired(String discriminatorPath,
                                            Set<String> allowedValues,
                                            Map<String, FieldDescriptor> requiredFields,
                                            List<Trigger> inheritedTriggers) {
        if (requiredFields.containsKey(discriminatorPath)) {
            return;
        }
        List<FeelLiteral> sortedValues = allowedValues.stream()
            .sorted()
            .<FeelLiteral>map(FeelString::new)
            .toList();
        requiredFields.put(discriminatorPath, new FieldDescriptor(
            StringTypeInfo.PLAIN, false, sortedValues, inheritedTriggers));
    }

    private void processComposition(List<?> schemas, Map<String, FieldDescriptor> requiredFields,
                                    String pathPrefix, Set<Schema<?>> activeStack,
                                    List<Trigger> inheritedTriggers) {
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        for (Object element : schemas) {
            if (element instanceof Schema<?> composedSchema) {
                collectRequiredFields(composedSchema, requiredFields, pathPrefix, activeStack, inheritedTriggers);
            }
        }
    }

    /**
     * For each OBJECT-typed property at this level, decide whether to recurse into its schema:
     * <ul>
     *   <li>Not in {@code requiredFields} → skip (parent is plain-optional, inner required fields are dropped).</li>
     *   <li>Unconditionally required → recurse, inner fields stay unconditional.</li>
     *   <li>Conditionally required → recurse, inner fields inherit the parent's triggers.</li>
     * </ul>
     */
    @SuppressWarnings("rawtypes") // Schema's API exposes Map<String, Schema> raw.
    private void processNestedProperties(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                                         String pathPrefix, Set<Schema<?>> activeStack) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        List<String> propertyNames = new ArrayList<>(properties.keySet());
        Collections.sort(propertyNames);
        for (String propName : propertyNames) {
            Schema<?> propSchema = properties.get(propName);
            String newPath = buildFieldPath(pathPrefix, propName);
            FieldDescriptor descriptor = typeResolver.resolve(propSchema);
            if (!(descriptor.typeInfo() instanceof ObjectTypeInfo)) {
                continue;
            }
            FieldDescriptor parent = requiredFields.get(newPath);
            if (parent == null) {
                continue;
            }
            List<Trigger> downstream = parent.isConditional() ? parent.dependsOn() : List.of();
            collectRequiredFields(propSchema, requiredFields, newPath, activeStack, downstream);
        }
    }

    private String buildFieldPath(String pathPrefix, String fieldName) {
        return pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
    }

    private String locationLabel(String pathPrefix) {
        return pathPrefix.isEmpty() ? "(root)" : pathPrefix;
    }
}
