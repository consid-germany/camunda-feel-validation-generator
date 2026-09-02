package com.consid.automation.camunda.internal.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Walks an OpenAPI document and yields the validatable inputs — request body
 * schema and required query parameters — for each operation that matches the
 * configured HTTP methods and media type.
 * Keys in the returned map are FEEL output headings (e.g. {@code "# POST /customers"})
 * so downstream rendering can group rules by endpoint without further plumbing.
 */
public final class OpenApiOperationScanner {

    private static final String QUERY = "query";

    private final List<String> httpMethods;
    private final String mediaType;

    public OpenApiOperationScanner(List<String> httpMethods, String mediaType) {
        this.httpMethods = List.copyOf(Objects.requireNonNull(httpMethods, "httpMethods"));
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
    }

    public Map<String, OperationInputs> scan(OpenAPI openAPI) {
        Map<String, OperationInputs> inputsByEndpoint = new LinkedHashMap<>();
        if (openAPI.getPaths() == null) {
            return inputsByEndpoint;
        }
        openAPI.getPaths().forEach((path, pathItem) ->
            collectFromPath(path, pathItem, inputsByEndpoint));
        return inputsByEndpoint;
    }

    private void collectFromPath(String path, PathItem pathItem, Map<String, OperationInputs> sink) {
        Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
        if (operations == null || operations.isEmpty()) {
            return;
        }
        for (String configuredMethod : httpMethods) {
            PathItem.HttpMethod httpMethod = parseHttpMethod(configuredMethod);
            if (httpMethod == null) {
                continue;
            }
            Operation operation = operations.get(httpMethod);
            if (operation == null) {
                continue;
            }
            OperationInputs inputs = new OperationInputs(
                requestBodySchema(operation), requiredQueryParameters(pathItem, operation));
            if (!inputs.isEmpty()) {
                sink.put("# " + httpMethod.name() + " " + path, inputs);
            }
        }
    }

    private Schema<?> requestBodySchema(Operation operation) {
        RequestBody body = operation.getRequestBody();
        if (body == null || body.getContent() == null) {
            return null;
        }
        MediaType media = body.getContent().get(mediaType);
        if (media == null) {
            return null;
        }
        return media.getSchema();
    }

    /**
     * Path-level parameters apply to every operation of the path; an operation-level
     * parameter with the same name overrides its path-level counterpart. Only
     * {@code in: query} parameters that end up {@code required: true} survive —
     * headers, cookies, and optional parameters carry no FEEL requirement.
     * {@code $ref} parameters are already inlined by the parser's resolve step.
     */
    private static List<Parameter> requiredQueryParameters(PathItem pathItem, Operation operation) {
        Map<String, Parameter> byName = new LinkedHashMap<>();
        addQueryParameters(pathItem.getParameters(), byName);
        addQueryParameters(operation.getParameters(), byName);
        return byName.values().stream()
            .filter(parameter -> Boolean.TRUE.equals(parameter.getRequired()))
            .toList();
    }

    private static void addQueryParameters(List<Parameter> parameters, Map<String, Parameter> sink) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            if (QUERY.equals(parameter.getIn()) && parameter.getName() != null) {
                sink.put(parameter.getName(), parameter);
            }
        }
    }

    private static PathItem.HttpMethod parseHttpMethod(String configuredMethod) {
        try {
            return PathItem.HttpMethod.valueOf(configuredMethod.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
