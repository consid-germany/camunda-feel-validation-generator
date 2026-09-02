package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.model.StringTypeInfo;
import com.consid.automation.camunda.internal.model.UnknownTypeInfo;

import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns an operation's required query parameters into name → {@link FieldDescriptor}
 * entries, sorted by name so the emitted rules are deterministic.
 *
 * <p>Query parameter values reach the webhook connector as strings — the
 * {@code request.params} context is string-valued — so only string-family
 * schemas keep their constraints (format, length, pattern, enum). Any other
 * declared type (integer, number, boolean, array) collapses to
 * {@link UnknownTypeInfo}, i.e. a pure presence check; a numeric type check
 * would reject every well-formed request. {@code nullable} is ignored for the
 * same reason: {@code required: true} on a query parameter means "must be present".
 */
public final class QueryParameterExtractor {

    private final FieldTypeResolver typeResolver;

    public QueryParameterExtractor(FieldTypeResolver typeResolver) {
        this.typeResolver = Objects.requireNonNull(typeResolver, "typeResolver");
    }

    public Map<String, FieldDescriptor> extract(List<Parameter> requiredQueryParameters) {
        return requiredQueryParameters.stream()
            .sorted(Comparator.comparing(Parameter::getName))
            .collect(Collectors.toMap(
                Parameter::getName, this::describe, (first, second) -> second, LinkedHashMap::new));
    }

    private FieldDescriptor describe(Parameter parameter) {
        FieldDescriptor resolved = typeResolver.resolve(parameter.getSchema());
        if (resolved.typeInfo() instanceof StringTypeInfo stringType) {
            return new FieldDescriptor(stringType, false, resolved.enumValues(), List.of());
        }
        return FieldDescriptor.of(UnknownTypeInfo.INSTANCE);
    }
}
