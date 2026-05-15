package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.Diagnostics;
import com.consid.automation.camunda.internal.model.*;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps an OpenAPI schema to a {@link FieldDescriptor}. Owns the OpenAPI-side
 * vocabulary (type/format/minimum/maximum/etc.) and produces a single sealed
 * {@link TypeInfo} per schema. Stays out of FEEL rendering — that's
 * the {@link com.consid.automation.camunda.feel.FEELExpressionBuilder}'s job.
 */
public class FieldTypeResolver {

    private static final String SCHEMA_PATH_PREFIX = "#/components/schemas/";

    /**
     * Canonical regexes injected when the schema declares {@code format: <name>}
     * but no explicit {@code pattern}. Deliberately permissive — author intent is
     * "looks like a UUID / email / URI", not RFC-perfect validation.
     */
    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final String UUID_PATTERN =
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static final String URI_PATTERN = "^[a-zA-Z][a-zA-Z0-9+.-]*:.+$";

    private final OpenAPI openAPI;
    private final Diagnostics diagnostics;

    public FieldTypeResolver(OpenAPI openAPI) {
        this(openAPI, Diagnostics.NOOP);
    }

    public FieldTypeResolver(OpenAPI openAPI, Diagnostics diagnostics) {
        this.openAPI = openAPI;
        this.diagnostics = diagnostics;
    }

    /**
     * Build a {@link FieldDescriptor} for the given schema, resolving any
     * {@code $ref} first.
     */
    public FieldDescriptor resolve(Schema<?> schema) {
        if (schema == null) {
            return FieldDescriptor.of(UnknownTypeInfo.INSTANCE);
        }
        Schema<?> resolved = resolveSchemaReference(schema);
        TypeInfo typeInfo = typeInfoFor(resolved);
        boolean nullable = isNullable(resolved);
        List<FeelLiteral> enumValues = enumValuesFrom(resolved);
        return new FieldDescriptor(typeInfo, nullable, enumValues, List.of());
    }

    /**
     * Public for the extractor's reference-resolution needs (e.g., when reading
     * the root schema directly). Throws if the {@code $ref} can't be resolved —
     * a broken spec should fail loud rather than silently emit UNKNOWN rules.
     */
    public Schema<?> resolveSchemaReference(Schema<?> schema) {
        if (schema == null) {
            return null;
        }
        String ref = schema.get$ref();
        if (ref == null || !ref.startsWith(SCHEMA_PATH_PREFIX)) {
            return schema;
        }
        String schemaName = ref.substring(SCHEMA_PATH_PREFIX.length());
        Components components = openAPI.getComponents();
        @SuppressWarnings("rawtypes")
        Map<String, Schema> schemas = components == null ? null : components.getSchemas();
        Schema<?> resolved = schemas == null ? null : schemas.get(schemaName);
        if (resolved == null) {
            throw new IllegalStateException("Unresolved $ref: " + ref);
        }
        return resolved;
    }

    private TypeInfo typeInfoFor(Schema<?> schema) {
        String primary = primaryType(schema);
        if (primary == null) {
            return schemaIndicatesObject(schema) ? objectTypeInfo(schema) : UnknownTypeInfo.INSTANCE;
        }
        return switch (primary.toLowerCase(Locale.ROOT)) {
            case "string" -> stringTypeInfo(schema);
            case "number", "integer" -> numberTypeInfo(schema);
            case "boolean" -> BooleanTypeInfo.INSTANCE;
            case "array" -> arrayTypeInfo(schema);
            case "object" -> objectTypeInfo(schema);
            default -> UnknownTypeInfo.INSTANCE;
        };
    }

    private StringTypeInfo stringTypeInfo(Schema<?> schema) {
        StringTypeInfo.StringFormat format = stringFormat(schema.getFormat());
        Integer minLength = schema.getMinLength();
        Integer maxLength = schema.getMaxLength();
        String pattern = schema.getPattern();
        if (pattern == null || pattern.isEmpty()) {
            pattern = formatPattern(schema.getFormat());
        }
        return new StringTypeInfo(format, minLength, maxLength, pattern);
    }

    /**
     * Returns the canonical regex for known string formats, or {@code null} if
     * the format isn't recognized. Authors who write an explicit {@code pattern}
     * always win over the format default.
     */
    private String formatPattern(String format) {
        if (format == null) {
            return null;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "email" -> EMAIL_PATTERN;
            case "uuid" -> UUID_PATTERN;
            case "uri", "url" -> URI_PATTERN;
            default -> null;
        };
    }

    private StringTypeInfo.StringFormat stringFormat(String format) {
        if (format == null) {
            return StringTypeInfo.StringFormat.PLAIN;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "date" -> StringTypeInfo.StringFormat.DATE;
            case "date-time" -> StringTypeInfo.StringFormat.DATE_TIME;
            case "time" -> StringTypeInfo.StringFormat.TIME;
            default -> StringTypeInfo.StringFormat.PLAIN;
        };
    }

    /**
     * Captures numeric range and divisibility, normalizing the OpenAPI 3.0
     * boolean exclusive form (where {@code exclusiveMinimum: true} promotes
     * {@code minimum} to exclusive) into the OpenAPI 3.1 numeric form.
     */
    private NumberTypeInfo numberTypeInfo(Schema<?> schema) {
        BigDecimal inclusiveMin = schema.getMinimum();
        BigDecimal exclusiveMin = schema.getExclusiveMinimumValue();
        if (exclusiveMin == null && Boolean.TRUE.equals(schema.getExclusiveMinimum()) && inclusiveMin != null) {
            exclusiveMin = inclusiveMin;
            inclusiveMin = null;
        }
        BigDecimal inclusiveMax = schema.getMaximum();
        BigDecimal exclusiveMax = schema.getExclusiveMaximumValue();
        if (exclusiveMax == null && Boolean.TRUE.equals(schema.getExclusiveMaximum()) && inclusiveMax != null) {
            exclusiveMax = inclusiveMax;
            inclusiveMax = null;
        }
        return new NumberTypeInfo(inclusiveMin, exclusiveMin, inclusiveMax, exclusiveMax, schema.getMultipleOf());
    }

    private ArrayTypeInfo arrayTypeInfo(Schema<?> schema) {
        FieldDescriptor items = schema.getItems() == null ? null : resolve(schema.getItems());
        return new ArrayTypeInfo(schema.getMinItems(), schema.getMaxItems(), items, Map.of());
    }

    /**
     * Captures {@code additionalProperties: false} as a closed allowed-key set.
     * The boolean {@code true} (or absence) leaves the object open. Schema-form
     * {@code additionalProperties} (a sub-schema specifying value types) is not
     * supported and is reported as a warning so the author doesn't silently get
     * an open object when they expected a typed-additional-properties constraint.
     */
    private ObjectTypeInfo objectTypeInfo(Schema<?> schema) {
        Object additionalProperties = schema.getAdditionalProperties();
        if (additionalProperties == null || Boolean.TRUE.equals(additionalProperties)) {
            return ObjectTypeInfo.OPEN;
        }
        if (Boolean.FALSE.equals(additionalProperties)) {
            var properties = schema.getProperties();
            Set<String> allowed = properties == null ? Set.of() : new LinkedHashSet<>(properties.keySet());
            return new ObjectTypeInfo(allowed);
        }
        diagnostics.warn(schemaLocation(schema),
            "schema-form `additionalProperties` is not supported; "
                + "only `additionalProperties: false` is honored");
        return ObjectTypeInfo.OPEN;
    }

    /**
     * Best-effort label for warnings: prefers the schema's $ref or declared name,
     * falls back to {@code (unnamed)}. Not a full JSON Pointer — swagger-parser
     * doesn't surface one — but enough to help the author locate the offending schema.
     */
    private String schemaLocation(Schema<?> schema) {
        if (schema.get$ref() != null) {
            return schema.get$ref();
        }
        if (schema.getName() != null) {
            return schema.getName();
        }
        return "(unnamed)";
    }

    /**
     * Returns the schema's primary type, preferring OpenAPI 3.0's single
     * {@code type} but falling back to the first non-"null" entry from
     * OpenAPI 3.1's {@code types} array.
     */
    private String primaryType(Schema<?> schema) {
        String singleType = schema.getType();
        if (singleType != null) {
            return singleType;
        }
        Set<String> types = schema.getTypes();
        if (types == null) {
            return null;
        }
        for (String candidate : types) {
            if (candidate != null && !"null".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Reads the field's allowed-value set. {@code enum} takes precedence; if absent,
     * {@code const} is promoted to a single-element enum so the existing in-check
     * renders the pinning constraint without any builder-side change.
     */
    private List<FeelLiteral> enumValuesFrom(Schema<?> schema) {
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return FeelLiteral.listOf(schema.getEnum());
        }
        if (schema.getConst() != null) {
            return List.of(FeelLiteral.of(schema.getConst()));
        }
        return List.of();
    }

    private boolean isNullable(Schema<?> schema) {
        if (Boolean.TRUE.equals(schema.getNullable())) {
            return true;
        }
        Set<String> types = schema.getTypes();
        if (types == null) {
            return false;
        }
        return types.stream().anyMatch(t -> "null".equalsIgnoreCase(t));
    }

    private boolean schemaIndicatesObject(Schema<?> schema) {
        return schema.getProperties() != null
            || schema.getRequired() != null
            || schema.getAllOf() != null
            || schema.getOneOf() != null
            || schema.getAnyOf() != null;
    }
}
