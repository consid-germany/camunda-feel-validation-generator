package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.Diagnostics;
import com.consid.automation.camunda.internal.model.ArrayTypeInfo;
import com.consid.automation.camunda.internal.model.FeelLiteral;
import com.consid.automation.camunda.internal.model.FeelString;
import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.model.ObjectTypeInfo;
import com.consid.automation.camunda.internal.model.StringTypeInfo;
import com.consid.automation.camunda.internal.model.Trigger;

import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Walks an OpenAPI request-body schema and produces a path-keyed map of
 * {@link FieldDescriptor}s for everything the FEEL generator must enforce:
 * direct required fields, dependent-required dependents, if/then dependents,
 * discriminated oneOf branches, and nested-object inner required fields with
 * trigger inheritance.
 */
public class RequiredFieldsExtractor {

    private final FieldTypeResolver typeResolver;
    private final Diagnostics diagnostics;

    public RequiredFieldsExtractor(FieldTypeResolver typeResolver, Diagnostics diagnostics) {
        this.typeResolver = Objects.requireNonNull(typeResolver, "typeResolver");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public ExtractionResult extract(Schema<?> schema) {
        Traversal traversal = Traversal.root();
        collect(schema, traversal);
        return new ExtractionResult(traversal.requiredFields(), rootClosureFor(schema));
    }

    /**
     * State threaded through the recursive walk. {@code requiredFields} is the
     * shared sink. {@code pathPrefix} and {@code inheritedTriggers} say where in
     * the payload we are and under which condition. {@code activeStack} holds the
     * schemas on the current recursion path, so a self-referential schema
     * terminates while a component reused at several paths is expanded each time.
     */
    private record Traversal(Map<String, FieldDescriptor> requiredFields,
                             String pathPrefix,
                             Set<Schema<?>> activeStack,
                             List<Trigger> inheritedTriggers) {

        static Traversal root() {
            return new Traversal(new LinkedHashMap<>(), "",
                Collections.newSetFromMap(new IdentityHashMap<>()), List.of());
        }

        /** Same sink and stack, descending into a nested object under the given triggers. */
        Traversal descend(String path, List<Trigger> triggers) {
            return new Traversal(requiredFields, path, activeStack, triggers);
        }

        /** Same position, different triggers (used for discriminated {@code oneOf} branches). */
        Traversal withTriggers(List<Trigger> triggers) {
            return new Traversal(requiredFields, pathPrefix, activeStack, triggers);
        }

        boolean isConditional() {
            return !inheritedTriggers.isEmpty();
        }

        List<Trigger> triggersPlus(Trigger trigger) {
            List<Trigger> merged = new ArrayList<>(inheritedTriggers);
            merged.add(trigger);
            return merged;
        }

        String fieldPath(String fieldName) {
            return pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
        }

        String location() {
            return pathPrefix.isEmpty() ? "(root)" : pathPrefix;
        }
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
        if (typeResolver.resolve(root).typeInfo() instanceof ObjectTypeInfo object && object.isClosed()) {
            return object;
        }
        return null;
    }

    private void collect(Schema<?> schema, Traversal traversal) {
        Schema<?> resolved = typeResolver.resolveSchemaReference(schema);
        if (resolved == null || !traversal.activeStack().add(resolved)) {
            return;
        }
        try {
            collectDirectRequired(resolved, traversal);
            collectDependentRequired(resolved, traversal);
            collectConditional(resolved, traversal);
            collectComposition(resolved.getAllOf(), traversal);
            collectOneOf(resolved, traversal);
            collectComposition(resolved.getAnyOf(), traversal);
            collectNestedObjects(resolved, traversal);
        } finally {
            traversal.activeStack().remove(resolved);
        }
    }

    private void collectDirectRequired(Schema<?> schema, Traversal traversal) {
        if (schema.getRequired() == null) {
            return;
        }
        for (String fieldName : sorted(schema.getRequired())) {
            String fieldPath = traversal.fieldPath(fieldName);
            if (traversal.requiredFields().containsKey(fieldPath)) {
                continue;
            }
            FieldDescriptor base = describeProperty(schema, fieldName, traversal);
            traversal.requiredFields().put(fieldPath,
                traversal.isConditional() ? base.withDependsOn(traversal.inheritedTriggers()) : base);
        }
    }

    /**
     * Resolves the schema of a named property, looking through {@code allOf}
     * branches (and their {@code $ref}s) when the property isn't declared on the
     * schema itself. A property found nowhere still yields a presence-only
     * descriptor, with a warning so a typo in {@code required} doesn't pass silently.
     */
    private FieldDescriptor describeProperty(Schema<?> schema, String fieldName, Traversal traversal) {
        Schema<?> propertySchema = findPropertySchema(schema, fieldName,
            Collections.newSetFromMap(new IdentityHashMap<>()));
        if (propertySchema == null) {
            diagnostics.warn(traversal.location(), "required property `" + fieldName
                + "` is not declared in `properties` (nor in any `allOf` branch); "
                + "only a presence check is emitted");
        }
        return enrichArrayItems(typeResolver.resolve(propertySchema), propertySchema);
    }

    @SuppressWarnings("rawtypes") // Schema's API exposes Map<String, Schema> raw.
    private Schema<?> findPropertySchema(Schema<?> schema, String fieldName, Set<Schema<?>> visited) {
        Schema<?> resolved = typeResolver.resolveSchemaReference(schema);
        if (resolved == null || !visited.add(resolved)) {
            return null;
        }
        Map<String, Schema> properties = resolved.getProperties();
        if (properties != null && properties.get(fieldName) != null) {
            return properties.get(fieldName);
        }
        if (resolved.getAllOf() == null) {
            return null;
        }
        for (Object branch : resolved.getAllOf()) {
            if (branch instanceof Schema<?> branchSchema) {
                Schema<?> found = findPropertySchema(branchSchema, fieldName, visited);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Walks an array's {@code items} schema for required fields and attaches
     * them to the descriptor's {@link ArrayTypeInfo}. Without this, an array of
     * objects passes validation as long as the list is well-typed — the
     * element-level required-field checks never get emitted.
     */
    private FieldDescriptor enrichArrayItems(FieldDescriptor descriptor, Schema<?> propertySchema) {
        if (!(descriptor.typeInfo() instanceof ArrayTypeInfo array)
            || propertySchema == null || propertySchema.getItems() == null) {
            return descriptor;
        }
        Traversal items = Traversal.root();
        collect(propertySchema.getItems(), items);
        return descriptor.withTypeInfo(new ArrayTypeInfo(
            array.minItems(), array.maxItems(), array.items(), items.requiredFields()));
    }

    private void collectDependentRequired(Schema<?> schema, Traversal traversal) {
        Map<String, List<String>> dependentRequired = schema.getDependentRequired();
        if (dependentRequired == null || dependentRequired.isEmpty()) {
            return;
        }
        for (String triggerName : sorted(dependentRequired.keySet())) {
            Trigger presence = Trigger.presence(traversal.fieldPath(triggerName));
            for (String dependent : sorted(dependentRequired.get(triggerName))) {
                addConditional(schema, dependent, presence, traversal);
            }
        }
    }

    /**
     * Reads the supported subset of JSON Schema {@code if}/{@code then}: a
     * single-property predicate with {@code const} or {@code enum} plus
     * {@code required: [<that property>]}, and a {@code then} block listing
     * required field names. Shapes outside this subset are reported and skipped.
     */
    private void collectConditional(Schema<?> schema, Traversal traversal) {
        Schema<?> ifSchema = schema.getIf();
        Schema<?> thenSchema = schema.getThen();
        if (ifSchema == null || thenSchema == null) {
            return;
        }
        Trigger trigger = extractValueTrigger(ifSchema, traversal);
        if (trigger == null) {
            diagnostics.warn(traversal.location(),
                "if/then predicate shape not supported and the conditional was skipped; "
                    + "only a single-property predicate using `const` or `enum` "
                    + "(plus `required: [<that property>]`) is honored");
            return;
        }
        List<String> thenRequired = thenSchema.getRequired();
        if (thenRequired == null) {
            return;
        }
        for (String dependent : sorted(thenRequired)) {
            addConditional(schema, dependent, trigger, traversal);
        }
    }

    /** Pulls a value trigger out of a supported {@code if} subschema, or null when the shape isn't handled. */
    @SuppressWarnings("rawtypes")
    private Trigger extractValueTrigger(Schema<?> ifSchema, Traversal traversal) {
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
        List<FeelLiteral> allowedValues = literalValues(ifProperties.get(triggerProperty));
        if (allowedValues.isEmpty()) {
            return null;
        }
        return Trigger.value(traversal.fieldPath(triggerProperty), allowedValues);
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

    private void addConditional(Schema<?> schema, String fieldName, Trigger trigger, Traversal traversal) {
        String fieldPath = traversal.fieldPath(fieldName);
        FieldDescriptor existing = traversal.requiredFields().get(fieldPath);
        if (existing != null) {
            // An unconditional requirement already wins; several conditional triggers OR-merge.
            if (existing.isConditional() && !existing.dependsOn().contains(trigger)) {
                List<Trigger> merged = new ArrayList<>(existing.dependsOn());
                merged.add(trigger);
                traversal.requiredFields().put(fieldPath, existing.withDependsOn(merged));
            }
            return;
        }
        FieldDescriptor base = describeProperty(schema, fieldName, traversal);
        traversal.requiredFields().put(fieldPath, base.withDependsOn(traversal.triggersPlus(trigger)));
    }

    /**
     * Handles {@code oneOf}: with a {@link Discriminator} + explicit mapping
     * each branch's required fields become conditional on the discriminator
     * value, and the discriminator property itself is pinned to the enum of
     * mapping keys as an unconditional required field. Without a mapping,
     * falls back to union-merge and warns.
     */
    private void collectOneOf(Schema<?> schema, Traversal traversal) {
        List<?> oneOf = schema.getOneOf();
        if (oneOf == null || oneOf.isEmpty()) {
            return;
        }
        Discriminator discriminator = schema.getDiscriminator();
        Map<String, String> mapping = discriminator == null ? null : discriminator.getMapping();
        String propertyName = discriminator == null ? null : discriminator.getPropertyName();
        if (propertyName == null || mapping == null || mapping.isEmpty()) {
            diagnostics.warn(traversal.location(),
                "oneOf without `discriminator.mapping` falls back to union-merge "
                    + "(all branches' required fields are accumulated, which is stricter than the spec implies); "
                    + "add a `discriminator` with explicit `mapping` to scope branch fields to their type value");
            collectComposition(oneOf, traversal);
            return;
        }
        String discriminatorPath = traversal.fieldPath(propertyName);
        pinDiscriminator(discriminatorPath, mapping.keySet(), traversal);

        // Reverse-lookup: $ref → discriminator value, so each branch can find its trigger.
        Map<String, String> refToValue = new HashMap<>();
        mapping.forEach((value, ref) -> refToValue.put(ref, value));

        for (Object element : oneOf) {
            if (!(element instanceof Schema<?> branch)) {
                continue;
            }
            String discriminatorValue = branch.get$ref() == null ? null : refToValue.get(branch.get$ref());
            if (discriminatorValue == null) {
                // Branch not in the mapping: union-merge fallback for that branch alone.
                collect(branch, traversal);
            } else {
                Trigger branchTrigger = Trigger.value(discriminatorPath, List.of(new FeelString(discriminatorValue)));
                collect(branch, traversal.withTriggers(traversal.triggersPlus(branchTrigger)));
            }
        }
    }

    /**
     * Pins the discriminator property as an unconditionally required string
     * whose enum is the set of mapping keys. Without this the discriminator
     * would only appear conditionally on its own value — a missing property
     * would silently disable all branch checks.
     */
    private void pinDiscriminator(String discriminatorPath, Set<String> allowedValues, Traversal traversal) {
        if (traversal.requiredFields().containsKey(discriminatorPath)) {
            return;
        }
        List<FeelLiteral> sortedValues = allowedValues.stream()
            .sorted()
            .<FeelLiteral>map(FeelString::new)
            .toList();
        traversal.requiredFields().put(discriminatorPath, new FieldDescriptor(
            StringTypeInfo.PLAIN, false, sortedValues, traversal.inheritedTriggers()));
    }

    private void collectComposition(List<?> schemas, Traversal traversal) {
        if (schemas == null) {
            return;
        }
        for (Object element : schemas) {
            if (element instanceof Schema<?> composed) {
                collect(composed, traversal);
            }
        }
    }

    /**
     * For each object-typed property that is itself required at this level,
     * recurse into its schema. Inner fields stay unconditional under an
     * unconditionally required parent and inherit the parent's triggers under a
     * conditionally required one. Plain-optional parents are skipped, so their
     * inner required fields are dropped.
     */
    @SuppressWarnings("rawtypes") // Schema's API exposes Map<String, Schema> raw.
    private void collectNestedObjects(Schema<?> schema, Traversal traversal) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        for (String propertyName : sorted(properties.keySet())) {
            String path = traversal.fieldPath(propertyName);
            FieldDescriptor parent = traversal.requiredFields().get(path);
            if (parent == null) {
                continue;
            }
            Schema<?> propertySchema = properties.get(propertyName);
            if (!(typeResolver.resolve(propertySchema).typeInfo() instanceof ObjectTypeInfo)) {
                continue;
            }
            List<Trigger> downstream = parent.isConditional() ? parent.dependsOn() : List.of();
            collect(propertySchema, traversal.descend(path, downstream));
        }
    }

    private static List<String> sorted(Collection<String> names) {
        return names.stream().sorted().toList();
    }
}
