package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.model.Endpoint;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OpenApiOperationScanner.
 */
class OpenApiOperationScannerTest {

    private static final List<String> DEFAULT_METHODS = List.of("POST", "PUT", "PATCH");
    private static final String JSON = "application/json";

    @Test
    void test_scan_does_return_empty_when_paths_are_missing() {
        // given
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(new OpenAPI());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_scan_does_collect_matching_operation() {
        // given
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.POST, JSON, bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).containsOnlyKeys(new Endpoint("POST", "/customers"));
        assertThat(result.get(new Endpoint("POST", "/customers")).bodySchema()).isSameAs(bodySchema);
    }

    @Test
    void test_scan_does_skip_operations_outside_configured_methods() {
        // given
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.DELETE, JSON, bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_scan_does_skip_operations_without_request_body_or_required_query_parameters() {
        // given
        Operation operation = new Operation()
            .addParametersItem(queryParameter("verbose", false))
            .addParametersItem(headerParameter("X-Trace-Id", true));
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers", new PathItem().post(operation)));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_scan_does_collect_required_query_parameters_next_to_body() {
        // given
        Schema<?> bodySchema = new Schema<>().type("object");
        Parameter tenant = queryParameter("tenant", true);
        Operation operation = operationWithBody(JSON, bodySchema)
            .addParametersItem(tenant)
            .addParametersItem(queryParameter("verbose", false))
            .addParametersItem(headerParameter("X-Trace-Id", true));
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers", new PathItem().post(operation)));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        OperationInputs inputs = result.get(new Endpoint("POST", "/customers"));
        assertThat(inputs.bodySchema()).isSameAs(bodySchema);
        assertThat(inputs.requiredQueryParameters()).containsExactly(tenant);
    }

    @Test
    void test_scan_does_collect_operation_with_only_required_query_parameters() {
        // given
        Parameter since = queryParameter("since", true);
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers/sync", new PathItem().post(new Operation().addParametersItem(since))));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).containsOnlyKeys(new Endpoint("POST", "/customers/sync"));
        OperationInputs inputs = result.get(new Endpoint("POST", "/customers/sync"));
        assertThat(inputs.hasBody()).isFalse();
        assertThat(inputs.requiredQueryParameters()).containsExactly(since);
    }

    @Test
    void test_scan_does_merge_path_level_query_parameters_with_operation_override() {
        // given — path-level `tenant` is overridden by the operation's own `tenant`,
        // path-level `region` applies as-is.
        Parameter pathTenant = queryParameter("tenant", true);
        Parameter region = queryParameter("region", true);
        Parameter operationTenant = queryParameter("tenant", true).description("operation-level");
        Operation operation = new Operation().addParametersItem(operationTenant);
        PathItem pathItem = new PathItem()
            .addParametersItem(pathTenant)
            .addParametersItem(region)
            .post(operation);
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem("/customers", pathItem));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result.get(new Endpoint("POST", "/customers")).requiredQueryParameters())
            .containsExactly(operationTenant, region);
    }

    @Test
    void test_scan_does_drop_path_level_required_parameter_relaxed_to_optional_by_operation() {
        // given — the operation redeclares `tenant` as optional, which overrides the
        // path-level `required: true`; `region` is untouched and stays required.
        Parameter region = queryParameter("region", true);
        PathItem pathItem = new PathItem()
            .addParametersItem(queryParameter("tenant", true))
            .addParametersItem(region)
            .post(new Operation().addParametersItem(queryParameter("tenant", false)));
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem("/customers", pathItem));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result.get(new Endpoint("POST", "/customers")).requiredQueryParameters()).containsExactly(region);
    }

    @Test
    void test_scan_does_ignore_path_level_optional_query_parameters() {
        // given
        PathItem pathItem = new PathItem()
            .addParametersItem(queryParameter("verbose", false))
            .post(new Operation());
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem("/customers", pathItem));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_scan_does_skip_operations_without_matching_media_type() {
        // given
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.POST, "application/xml", bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_scan_does_pick_configured_media_type_among_many() {
        // given
        Schema<?> jsonSchema = new Schema<>().type("object").addProperty("json", new Schema<>().type("string"));
        Schema<?> xmlSchema = new Schema<>().type("object").addProperty("xml", new Schema<>().type("string"));
        Content content = new Content()
            .addMediaType(JSON, new MediaType().schema(jsonSchema))
            .addMediaType("application/xml", new MediaType().schema(xmlSchema));
        Operation operation = new Operation().requestBody(new RequestBody().content(content));
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers", new PathItem().post(operation)));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, "application/xml");

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).containsOnlyKeys(new Endpoint("POST", "/customers"));
        assertThat(result.get(new Endpoint("POST", "/customers")).bodySchema()).isSameAs(xmlSchema);
    }

    @Test
    void test_scan_does_match_media_type_ignoring_parameters() {
        // given
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers", new PathItem().post(operationWithBody("application/json; charset=utf-8", bodySchema))));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result.get(new Endpoint("POST", "/customers")).bodySchema()).isSameAs(bodySchema);
    }

    @Test
    void test_scan_does_match_structured_suffix_media_type() {
        // given
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers", new PathItem().post(operationWithBody("application/vnd.acme+json", bodySchema))));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result.get(new Endpoint("POST", "/customers")).bodySchema()).isSameAs(bodySchema);
    }

    @Test
    void test_scan_does_prefer_exact_media_type_over_suffix_match() {
        // given — the suffixed type is declared first, the exact one second
        Schema<?> vendorSchema = new Schema<>().type("object").addProperty("vendor", new Schema<>().type("string"));
        Schema<?> jsonSchema = new Schema<>().type("object").addProperty("json", new Schema<>().type("string"));
        Content content = new Content()
            .addMediaType("application/vnd.acme+json", new MediaType().schema(vendorSchema))
            .addMediaType("application/json", new MediaType().schema(jsonSchema));
        Operation operation = new Operation().requestBody(new RequestBody().content(content));
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem("/customers", new PathItem().post(operation)));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result.get(new Endpoint("POST", "/customers")).bodySchema()).isSameAs(jsonSchema);
    }

    @Test
    void test_scan_does_silently_skip_invalid_http_method_names() {
        // given
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.POST, JSON, bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(List.of("POST", "BREW"), JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result).containsOnlyKeys(new Endpoint("POST", "/customers"));
        assertThat(result.get(new Endpoint("POST", "/customers")).bodySchema()).isSameAs(bodySchema);
    }

    @Test
    void test_scan_does_preserve_path_order() {
        // given
        Schema<?> first = new Schema<>().type("object").addProperty("a", new Schema<>().type("string"));
        Schema<?> second = new Schema<>().type("object").addProperty("b", new Schema<>().type("string"));
        Paths paths = new Paths()
            .addPathItem("/first", new PathItem().post(operationWithBody(JSON, first)))
            .addPathItem("/second", new PathItem().post(operationWithBody(JSON, second)));
        OpenAPI openAPI = new OpenAPI().paths(paths);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        // when
        Map<Endpoint, OperationInputs> result = scanner.scan(openAPI);

        // then
        assertThat(result.keySet()).containsExactly(new Endpoint("POST", "/first"), new Endpoint("POST", "/second"));
    }

    @Test
    void test_constructor_does_reject_null_arguments() {
        // when // then
        assertThatThrownBy(() -> new OpenApiOperationScanner(null, JSON))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("httpMethods");
        assertThatThrownBy(() -> new OpenApiOperationScanner(DEFAULT_METHODS, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("mediaType");
    }

    private static OpenAPI openApiWith(String path, PathItem.HttpMethod method, String mediaType, Schema<?> schema) {
        PathItem pathItem = new PathItem();
        Operation operation = operationWithBody(mediaType, schema);
        switch (method) {
            case POST -> pathItem.post(operation);
            case PUT -> pathItem.put(operation);
            case PATCH -> pathItem.patch(operation);
            case DELETE -> pathItem.delete(operation);
            case GET -> pathItem.get(operation);
            default -> throw new IllegalArgumentException("Unsupported method in test fixture: " + method);
        }
        return new OpenAPI().paths(new Paths().addPathItem(path, pathItem));
    }

    private static Operation operationWithBody(String mediaType, Schema<?> schema) {
        Content content = new Content().addMediaType(mediaType, new MediaType().schema(schema));
        return new Operation().requestBody(new RequestBody().content(content));
    }

    private static Parameter queryParameter(String name, boolean required) {
        return new Parameter().name(name).in("query").required(required)
            .schema(new Schema<>().type("string"));
    }

    private static Parameter headerParameter(String name, boolean required) {
        return new Parameter().name(name).in("header").required(required)
            .schema(new Schema<>().type("string"));
    }
}
