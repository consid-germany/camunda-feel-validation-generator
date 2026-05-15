package com.consid.automation.camunda.internal.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Walks an OpenAPI document and yields the request body schema for each
 * operation that matches the configured HTTP methods and media type.
 * Keys in the returned map are FEEL output headings (e.g. {@code "# POST /customers"})
 * so downstream rendering can group rules by endpoint without further plumbing.
 */
public final class OpenApiOperationScanner {

    private final List<String> httpMethods;
    private final String mediaType;

    public OpenApiOperationScanner(List<String> httpMethods, String mediaType) {
        this.httpMethods = List.copyOf(Objects.requireNonNull(httpMethods, "httpMethods"));
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
    }

    public Map<String, Schema<?>> scan(OpenAPI openAPI) {
        Map<String, Schema<?>> schemasByEndpoint = new LinkedHashMap<>();
        if (openAPI.getPaths() == null) {
            return schemasByEndpoint;
        }
        openAPI.getPaths().forEach((path, pathItem) ->
            collectFromPath(path, pathItem, schemasByEndpoint));
        return schemasByEndpoint;
    }

    private void collectFromPath(String path, PathItem pathItem, Map<String, Schema<?>> sink) {
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
            Schema<?> schema = requestBodySchema(operation);
            if (schema != null) {
                sink.put("# " + httpMethod.name() + " " + path, schema);
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

    private static PathItem.HttpMethod parseHttpMethod(String configuredMethod) {
        try {
            return PathItem.HttpMethod.valueOf(configuredMethod.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
