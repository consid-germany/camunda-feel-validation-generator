package com.consid.automation.camunda;

import com.consid.automation.camunda.internal.feel.*;
import com.consid.automation.camunda.internal.model.*;
import com.consid.automation.camunda.internal.openapi.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.feel.FeelEngine;
import org.junit.jupiter.api.io.TempDir;
import scala.jdk.javaapi.CollectionConverters;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provides shared utilities for integration-style FEEL generator tests.
 * Contains only generic helpers for reading resources, parsing FEEL output,
 * and converting FEEL evaluation results into Java collections.
 */
public abstract class AbstractFEELValidationGeneratorIntegrationTest {

    @TempDir
    protected Path tempDir;

    protected static final FeelEngine FEEL_ENGINE = new FeelEngine(
        FeelEngine.defaultFunctionProvider(),
        FeelEngine.defaultValueMapper(),
        FeelEngine.defaultConfiguration(),
        FeelEngine.defaultClock()
    );

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * One end-to-end test case: a spec, a body payload, an optional query-parameter
     * payload (bound to {@code request.params}), and the boolean verdict the
     * activation FEEL is expected to produce. The same record drives the response
     * facade — response tests use {@link #expectedValid()} as the {@code isValid}
     * field's expected value while ignoring the body shape.
     *
     * <p>{@link #toString()} returns the bare id so JUnit's parameterized test
     * display shows {@code [1] customers-kitchen-sink-valid} instead of the full
     * record dump.
     */
    public record Scenario(String id,
                           String openApiResource,
                           String payloadResource,
                           String paramsResource,
                           boolean expectedValid) {

        /** Body-only scenario: {@code request.params} is an empty context. */
        public Scenario(String id, String openApiResource, String payloadResource, boolean expectedValid) {
            this(id, openApiResource, payloadResource, null, expectedValid);
        }

        public boolean hasParams() {
            return paramsResource != null;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /**
     * Single source of truth for integration scenarios, consumed by both facade
     * tests via {@code @MethodSource}. Each row spans one OpenAPI fixture +
     * payload combination; new mechanisms get their own fixture pair here.
     */
    public static Stream<Scenario> scenarios() {
        return Stream.of(
            new Scenario("customers-direct-valid",
                "openapi/customers-direct-api.json",
                "payloads/customers-direct-variables.json", true),
            new Scenario("customers-direct-invalid",
                "openapi/customers-direct-api.json",
                "payloads/customers-direct-invalid-variables.json", false),
            new Scenario("customers-referenced-valid",
                "openapi/customers-referenced-api.json",
                "payloads/customers-referenced-variables.json", true),
            new Scenario("customers-allOf-valid",
                "openapi/customers-allOf-api.json",
                "payloads/customers-allOf-variables.json", true),
            new Scenario("customers-oneOf-valid",
                "openapi/customers-oneOf-api.json",
                "payloads/customers-oneOf-variables.json", true),
            new Scenario("customers-anyOf-valid",
                "openapi/customers-anyOf-api.json",
                "payloads/customers-anyOf-variables.json", true),
            new Scenario("customers-shared-valid",
                "openapi/customers-shared-api.json",
                "payloads/customers-shared-variables.json", true),
            new Scenario("customers-conditional-no-shipping",
                "openapi/customers-conditional-api.json",
                "payloads/customers-conditional-no-shipping-variables.json", true),
            new Scenario("customers-conditional-missing-carrier",
                "openapi/customers-conditional-api.json",
                "payloads/customers-conditional-missing-carrier-variables.json", false),
            new Scenario("customers-value-conditional-invoice",
                "openapi/customers-value-conditional-api.json",
                "payloads/customers-value-conditional-invoice-variables.json", true),
            new Scenario("customers-value-conditional-card-without-number",
                "openapi/customers-value-conditional-api.json",
                "payloads/customers-value-conditional-card-without-number-variables.json", false),
            new Scenario("customers-value-conditional-card-with-number",
                "openapi/customers-value-conditional-api.json",
                "payloads/customers-value-conditional-card-with-number-variables.json", true),
            new Scenario("orders-no-delivery",
                "openapi/orders-conditional-nested-api.json",
                "payloads/orders-no-delivery-variables.json", true),
            new Scenario("orders-needs-delivery-without-address",
                "openapi/orders-conditional-nested-api.json",
                "payloads/orders-needs-delivery-without-address-variables.json", false),
            new Scenario("orders-needs-delivery-with-address",
                "openapi/orders-conditional-nested-api.json",
                "payloads/orders-needs-delivery-with-address-variables.json", true),
            new Scenario("customers-constraints-valid",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-valid-variables.json", true),
            new Scenario("customers-constraints-tags-empty",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-tags-empty-variables.json", false),
            new Scenario("customers-constraints-tags-too-many",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-tags-too-many-variables.json", false),
            new Scenario("customers-constraints-handle-too-short",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-handle-too-short-variables.json", false),
            new Scenario("customers-constraints-handle-too-long",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-handle-too-long-variables.json", false),
            new Scenario("customers-constraints-code-pattern-miss",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-code-pattern-miss-variables.json", false),
            new Scenario("customers-number-constraints-valid",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-valid-variables.json", true),
            new Scenario("customers-number-constraints-age-below-min",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-age-below-min-variables.json", false),
            new Scenario("customers-number-constraints-age-above-max",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-age-above-max-variables.json", false),
            new Scenario("customers-number-constraints-discount-at-exclusive-min",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-discount-at-exclusive-min-variables.json", false),
            new Scenario("customers-number-constraints-points-not-multiple",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-points-not-multiple-variables.json", false),
            new Scenario("orders-array-items-valid",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-valid-variables.json", true),
            new Scenario("orders-array-items-missing-sku",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-missing-sku-variables.json", false),
            new Scenario("orders-array-items-bad-sku-pattern",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-bad-sku-pattern-variables.json", false),
            new Scenario("orders-array-items-quantity-below-min",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-quantity-below-min-variables.json", false),
            new Scenario("events-formats-valid",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-valid-variables.json", true),
            new Scenario("events-formats-bad-uuid",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-bad-uuid-variables.json", false),
            new Scenario("events-formats-bad-email",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-bad-email-variables.json", false),
            new Scenario("events-formats-const-mismatch",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-const-mismatch-variables.json", false),
            new Scenario("customers-strict-valid",
                "openapi/customers-strict-api.json",
                "payloads/customers-strict-valid-variables.json", true),
            new Scenario("customers-strict-extra-root-key",
                "openapi/customers-strict-api.json",
                "payloads/customers-strict-extra-root-key-variables.json", false),
            new Scenario("customers-strict-extra-nested-key",
                "openapi/customers-strict-api.json",
                "payloads/customers-strict-extra-nested-key-variables.json", false),
            new Scenario("events-discriminator-paid-valid",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-paid-valid-variables.json", true),
            new Scenario("events-discriminator-failed-valid",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-failed-valid-variables.json", true),
            new Scenario("events-discriminator-paid-missing-paidat",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-paid-missing-paidat-variables.json", false),
            new Scenario("events-discriminator-unknown-type",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-unknown-type-variables.json", false),
            new Scenario("customers-kitchen-sink-valid",
                "openapi/customers-kitchen-sink-api.json",
                "payloads/customers-kitchen-sink-valid-variables.json", true),
            new Scenario("customers-kitchen-sink-invalid",
                "openapi/customers-kitchen-sink-api.json",
                "payloads/customers-kitchen-sink-invalid-variables.json", false),
            new Scenario("customers-query-params-valid",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-variables.json",
                "payloads/customers-query-params-valid-params.json", true),
            new Scenario("customers-query-params-missing-tenant",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-variables.json",
                "payloads/customers-query-params-missing-tenant-params.json", false),
            new Scenario("customers-query-params-tenant-not-in-enum",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-variables.json",
                "payloads/customers-query-params-tenant-not-in-enum-params.json", false),
            new Scenario("customers-query-params-bad-page-size",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-variables.json",
                "payloads/customers-query-params-bad-page-size-params.json", false),
            // Non-string query parameters are presence-only: values arrive as strings,
            // so a numeric type check would reject every well-formed request.
            new Scenario("customers-query-params-limit-not-numeric",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-variables.json",
                "payloads/customers-query-params-limit-not-numeric-params.json", true),
            new Scenario("customers-query-params-bad-request-id",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-variables.json",
                "payloads/customers-query-params-bad-request-id-params.json", false),
            new Scenario("customers-query-only-valid",
                "openapi/customers-query-only-api.json",
                "payloads/customers-query-only-variables.json",
                "payloads/customers-query-only-valid-params.json", true),
            new Scenario("customers-query-only-missing-since",
                "openapi/customers-query-only-api.json",
                "payloads/customers-query-only-variables.json",
                "payloads/customers-query-only-missing-since-params.json", false)
        );
    }

    protected Path resolveResourcePath(String resourceName) {
        URL resourceUrl = getClass().getClassLoader().getResource(resourceName);
        assertThat(resourceUrl)
            .as(resourceName + " should exist in test resources")
            .isNotNull();
        try {
            return Path.of(resourceUrl.toURI());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve resource path for " + resourceName, e);
        }
    }

    protected String readResourceFile(String resourceName) throws IOException {
        URL resourceUrl = getClass().getClassLoader().getResource(resourceName);
        assertThat(resourceUrl)
            .as(resourceName + " should exist in test resources")
            .isNotNull();

        try (InputStream inputStream = resourceUrl.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    protected Map<String, Object> loadJsonResource(String resourceName) throws IOException {
        String json = readResourceFile(resourceName);
        return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    protected List<String> extractFeelExpressions(String output) {
        List<String> expressions = new ArrayList<>();
        int index = 0;
        while (index < output.length()) {
            int headerStart = output.indexOf("# ", index);
            if (headerStart == -1) {
                break;
            }
            int blockStart = output.indexOf('\n', headerStart);
            if (blockStart == -1) {
                break;
            }
            blockStart += 1;
            int nextHeader = output.indexOf("\n# ", blockStart);
            String blockExpression;
            if (nextHeader == -1) {
                blockExpression = output.substring(blockStart).trim();
                expressions.add(blockExpression);
                break;
            } else {
                blockExpression = output.substring(blockStart, nextHeader).trim();
                expressions.add(blockExpression);
                index = nextHeader + 1;
            }
        }
        return expressions;
    }

    /**
     * Builds the webhook evaluation context for a scenario: the body payload under
     * {@code request.body} and, when the scenario declares one, the query-parameter
     * payload under {@code request.params} (the Camunda webhook connector exposes
     * query parameters there as a string-valued context).
     */
    protected Map<String, Object> buildEvaluationContext(Scenario scenario) throws IOException {
        Map<String, Object> body = loadJsonResource(scenario.payloadResource());
        Map<String, Object> params = scenario.hasParams()
            ? loadJsonResource(scenario.paramsResource())
            : Map.of();
        return buildEvaluationContext(body, params);
    }

    protected Map<String, Object> buildEvaluationContext(Map<String, Object> body) {
        return buildEvaluationContext(body, Map.of());
    }

    protected Map<String, Object> buildEvaluationContext(Map<String, Object> body, Map<String, Object> params) {
        assertThat(body)
            .as("Sample request body should exist for scenario")
            .isNotNull();
        Map<String, Object> request = Map.of(
            "body", body,
            "headers", Map.of(),
            "params", params
        );
        return Map.of(
            "request", request,
            "correlation", Map.of("processInstanceKey", 123456789L)
        );
    }

    protected String extractPath(String headerLine) {
        String[] parts = headerLine.split(" ", 3);
        return parts.length >= 3 ? parts[2].trim() : "";
    }

    protected Map<String, Object> toJavaMap(Object value) {
        if (value instanceof Map<?, ?> javaMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) javaMap;
            return casted;
        }
        if (value instanceof scala.collection.Map<?, ?> scalaMap) {
            return (Map<String, Object>) CollectionConverters.asJava(scalaMap);
        }
        throw new IllegalArgumentException("Unsupported map value: " + value);
    }

    protected List<Object> toJavaList(Object value) {
        if (value instanceof List<?> javaList) {
            @SuppressWarnings("unchecked")
            List<Object> casted = (List<Object>) javaList;
            return casted;
        }
        if (value instanceof scala.collection.Iterable<?> scalaIterable) {
            List<Object> converted = new ArrayList<>();
            CollectionConverters.asJava(scalaIterable).forEach(converted::add);
            return converted;
        }
        throw new IllegalArgumentException("Unsupported list value: " + value);
    }

    protected Map<String, Object> castToMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        throw new IllegalArgumentException("Value is not a map: " + value);
    }

    protected Object normalizeValue(Object value) {
        if (value instanceof scala.collection.Map<?, ?> scalaMap) {
            return normalizeValue(CollectionConverters.asJava(scalaMap));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, val) -> normalized.put(String.valueOf(key), normalizeValue(val)));
            return normalized;
        }
        if (value instanceof scala.collection.Iterable<?> scalaIterable) {
            List<Object> normalized = new ArrayList<>();
            CollectionConverters.asJava(scalaIterable).forEach(item -> normalized.add(normalizeValue(item)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object element : list) {
                normalized.add(normalizeValue(element));
            }
            return normalized;
        }
        return value;
    }
}
