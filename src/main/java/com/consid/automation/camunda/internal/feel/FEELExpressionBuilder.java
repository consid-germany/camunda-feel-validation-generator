package com.consid.automation.camunda.internal.feel;

import com.consid.automation.camunda.internal.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a {@link FieldDescriptor} as a FEEL "violation" expression — true
 * means the field is invalid. The body is a flat OR-chain composed of:
 * <ol>
 *   <li>missing-or-present guard (depends on {@code nullable}),</li>
 *   <li>type clause + type-specific constraint clauses (dispatched on the
 *       sealed {@link TypeInfo}),</li>
 *   <li>enum membership clause when an allowed-value set is set.</li>
 * </ol>
 * Conditional triggers wrap the body with a {@code guard and (body)} clause.
 */
public class FEELExpressionBuilder {

    public String build(String fieldName, FieldDescriptor descriptor) {
        String body = buildBody(fieldName, descriptor);
        if (!descriptor.isConditional()) {
            return body;
        }
        return guardExpression(descriptor.dependsOn()) + " and (" + body + ")";
    }

    private String buildBody(String fieldName, FieldDescriptor descriptor) {
        String violation = buildViolation(fieldName, descriptor);
        if (descriptor.nullable()) {
            return violation == null ? "false" : fieldName + "!=null and (" + violation + ")";
        }
        return violation == null ? fieldName + "=null" : fieldName + "=null or " + violation;
    }

    private String buildViolation(String fieldName, FieldDescriptor descriptor) {
        List<String> parts = typeViolations(fieldName, descriptor.typeInfo());
        if (descriptor.hasEnum()) {
            parts.add("not(" + fieldName + " in (" + renderLiterals(descriptor.enumValues()) + "))");
        }
        return parts.isEmpty() ? null : String.join(" or ", parts);
    }

    /**
     * Pattern-matches on the type info; each arm emits its own OR-chain of
     * "value violates the type" clauses (type-instance check first, then any
     * declared size / range / pattern bounds).
     */
    private List<String> typeViolations(String fieldName, TypeInfo typeInfo) {
        return switch (typeInfo) {
            case StringTypeInfo s -> stringViolations(fieldName, s);
            case NumberTypeInfo n -> numberViolations(fieldName, n);
            case BooleanTypeInfo b -> singletonOrEmpty("not(" + fieldName + " instance of boolean)");
            case ArrayTypeInfo a -> arrayViolations(fieldName, a);
            case ObjectTypeInfo o -> objectViolations(fieldName, o);
            case UnknownTypeInfo u -> List.of();
        };
    }

    private List<String> stringViolations(String fieldName, StringTypeInfo info) {
        List<String> parts = new ArrayList<>();
        parts.add(typeClause(fieldName, info.format()));
        if (info.hasMinLength()) {
            parts.add("string length(" + fieldName + ")<" + info.minLength());
        }
        if (info.hasMaxLength()) {
            parts.add("string length(" + fieldName + ")>" + info.maxLength());
        }
        if (info.hasPattern()) {
            parts.add("not(matches(" + fieldName + ", \"" + escapeLiteral(info.pattern()) + "\"))");
        }
        return parts;
    }

    /** FEEL has dedicated parsers for date/time families; plain strings use the type-instance check. */
    private String typeClause(String fieldName, StringTypeInfo.StringFormat format) {
        return switch (format) {
            case PLAIN -> "not(" + fieldName + " instance of string)";
            case DATE -> "date(" + fieldName + ")=null";
            case DATE_TIME -> "date and time(" + fieldName + ")=null";
            case TIME -> "time(" + fieldName + ")=null";
        };
    }

    private List<String> numberViolations(String fieldName, NumberTypeInfo info) {
        List<String> parts = new ArrayList<>();
        parts.add("not(" + fieldName + " instance of number)");
        if (info.hasMinimum()) {
            parts.add(fieldName + "<" + renderNumber(info.minimum()));
        }
        if (info.hasExclusiveMinimum()) {
            parts.add(fieldName + "<=" + renderNumber(info.exclusiveMinimum()));
        }
        if (info.hasMaximum()) {
            parts.add(fieldName + ">" + renderNumber(info.maximum()));
        }
        if (info.hasExclusiveMaximum()) {
            parts.add(fieldName + ">=" + renderNumber(info.exclusiveMaximum()));
        }
        if (info.hasMultipleOf()) {
            parts.add("modulo(" + fieldName + ", " + renderNumber(info.multipleOf()) + ")!=0");
        }
        return parts;
    }

    private List<String> arrayViolations(String fieldName, ArrayTypeInfo info) {
        List<String> parts = new ArrayList<>();
        parts.add("not(" + fieldName + " instance of list)");
        if (info.hasMinItems()) {
            parts.add("count(" + fieldName + ")<" + info.minItems());
        }
        if (info.hasMaxItems()) {
            parts.add("count(" + fieldName + ")>" + info.maxItems());
        }
        if (info.hasItems()) {
            // Outer parens around the whole `some ... satisfies ...` clause: FEEL's
            // quantifiedOp body is greedy and would otherwise consume tokens past
            // the intended end, breaking the surrounding OR-chain.
            parts.add("(some e in " + fieldName + " satisfies ("
                + elementViolation(info.items(), info.itemRequiredFields()) + "))");
        }
        return parts;
    }

    /**
     * Builds the per-element violation chain used inside a {@code some e in X
     * satisfies (...)} clause. The element binding is always {@code e}; nested
     * arrays rely on FEEL's lexical scoping to shadow correctly.
     */
    private String elementViolation(FieldDescriptor items, Map<String, FieldDescriptor> itemRequiredFields) {
        List<String> parts = new ArrayList<>();
        parts.add(build("e", items));
        itemRequiredFields.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> parts.add(build("e." + entry.getKey(), entry.getValue())));
        return String.join(" or ", parts);
    }

    private List<String> objectViolations(String fieldName, ObjectTypeInfo info) {
        List<String> parts = new ArrayList<>();
        parts.add("not(" + fieldName + " instance of context)");
        if (info.isClosed()) {
            String list = info.allowedKeys().stream()
                .sorted()
                .map(k -> "\"" + escapeLiteral(k) + "\"")
                .collect(Collectors.joining(", "));
            // `get entries(ctx).key` projects out the list of keys (Camunda FEEL has
            // no direct `get keys(...)` function). Outer parens defend against the
            // greedy `every ... satisfies <expr>` operator consuming surrounding tokens.
            parts.add("(not(every k in get entries(" + fieldName
                + ").key satisfies (k in (" + list + "))))");
        }
        return parts;
    }

    private String guardExpression(List<Trigger> dependsOn) {
        String parts = dependsOn.stream()
            .map(this::renderTrigger)
            .collect(Collectors.joining(" or "));
        return dependsOn.size() == 1 ? parts : "(" + parts + ")";
    }

    private String renderTrigger(Trigger trigger) {
        return switch (trigger) {
            case PresenceTrigger p -> p.path() + "!=null";
            case ValueTrigger v -> renderValueTrigger(v);
        };
    }

    private String renderValueTrigger(ValueTrigger trigger) {
        if (trigger.allowedValues().size() == 1) {
            FeelLiteral value = trigger.allowedValues().get(0);
            // Booleans render as bare path / not(path) since FEEL treats them identically to the
            // explicit =true / =false comparison, including under null inputs.
            if (value instanceof FeelBoolean bool) {
                return bool.value() ? trigger.path() : "not(" + trigger.path() + ")";
            }
            return trigger.path() + "=" + value.render();
        }
        return trigger.path() + " in (" + renderLiterals(trigger.allowedValues()) + ")";
    }

    private String renderLiterals(List<FeelLiteral> values) {
        return values.stream()
            .map(FeelLiteral::render)
            .collect(Collectors.joining(", "));
    }

    private static String renderNumber(java.math.BigDecimal value) {
        return value.toPlainString();
    }

    private static String escapeLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> singletonOrEmpty(String value) {
        List<String> parts = new ArrayList<>();
        parts.add(value);
        return parts;
    }
}
