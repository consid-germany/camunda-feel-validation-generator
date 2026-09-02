package com.consid.automation.camunda;

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
import java.util.Arrays;
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
                "payloads/customers-direct-body.json", true),
            new Scenario("customers-direct-invalid",
                "openapi/customers-direct-api.json",
                "payloads/customers-direct-invalid-body.json", false),
            new Scenario("customers-referenced-valid",
                "openapi/customers-referenced-api.json",
                "payloads/customers-referenced-body.json", true),
            new Scenario("customers-allOf-valid",
                "openapi/customers-allOf-api.json",
                "payloads/customers-allOf-body.json", true),
            new Scenario("customers-oneOf-valid",
                "openapi/customers-oneOf-api.json",
                "payloads/customers-oneOf-body.json", true),
            new Scenario("customers-anyOf-valid",
                "openapi/customers-anyOf-api.json",
                "payloads/customers-anyOf-body.json", true),
            new Scenario("customers-shared-valid",
                "openapi/customers-shared-api.json",
                "payloads/customers-shared-body.json", true),
            new Scenario("customers-conditional-no-shipping",
                "openapi/customers-conditional-api.json",
                "payloads/customers-conditional-no-shipping-body.json", true),
            new Scenario("customers-conditional-missing-carrier",
                "openapi/customers-conditional-api.json",
                "payloads/customers-conditional-missing-carrier-body.json", false),
            new Scenario("customers-value-conditional-invoice",
                "openapi/customers-value-conditional-api.json",
                "payloads/customers-value-conditional-invoice-body.json", true),
            new Scenario("customers-value-conditional-card-without-number",
                "openapi/customers-value-conditional-api.json",
                "payloads/customers-value-conditional-card-without-number-body.json", false),
            new Scenario("customers-value-conditional-card-with-number",
                "openapi/customers-value-conditional-api.json",
                "payloads/customers-value-conditional-card-with-number-body.json", true),
            new Scenario("orders-no-delivery",
                "openapi/orders-conditional-nested-api.json",
                "payloads/orders-no-delivery-body.json", true),
            new Scenario("orders-needs-delivery-without-address",
                "openapi/orders-conditional-nested-api.json",
                "payloads/orders-needs-delivery-without-address-body.json", false),
            new Scenario("orders-needs-delivery-with-address",
                "openapi/orders-conditional-nested-api.json",
                "payloads/orders-needs-delivery-with-address-body.json", true),
            new Scenario("customers-constraints-valid",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-valid-body.json", true),
            new Scenario("customers-constraints-tags-empty",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-tags-empty-body.json", false),
            new Scenario("customers-constraints-tags-too-many",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-tags-too-many-body.json", false),
            new Scenario("customers-constraints-handle-too-short",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-handle-too-short-body.json", false),
            new Scenario("customers-constraints-handle-too-long",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-handle-too-long-body.json", false),
            new Scenario("customers-constraints-code-pattern-miss",
                "openapi/customers-constraints-api.json",
                "payloads/customers-constraints-code-pattern-miss-body.json", false),
            new Scenario("customers-number-constraints-valid",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-valid-body.json", true),
            new Scenario("customers-number-constraints-age-below-min",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-age-below-min-body.json", false),
            new Scenario("customers-number-constraints-age-above-max",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-age-above-max-body.json", false),
            new Scenario("customers-number-constraints-discount-at-exclusive-min",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-discount-at-exclusive-min-body.json", false),
            new Scenario("customers-number-constraints-points-not-multiple",
                "openapi/customers-number-constraints-api.json",
                "payloads/customers-number-constraints-points-not-multiple-body.json", false),
            new Scenario("orders-array-items-valid",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-valid-body.json", true),
            new Scenario("orders-array-items-missing-sku",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-missing-sku-body.json", false),
            new Scenario("orders-array-items-bad-sku-pattern",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-bad-sku-pattern-body.json", false),
            new Scenario("orders-array-items-quantity-below-min",
                "openapi/orders-array-items-api.json",
                "payloads/orders-array-items-quantity-below-min-body.json", false),
            new Scenario("events-formats-valid",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-valid-body.json", true),
            new Scenario("events-formats-bad-uuid",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-bad-uuid-body.json", false),
            new Scenario("events-formats-bad-email",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-bad-email-body.json", false),
            new Scenario("events-formats-const-mismatch",
                "openapi/events-formats-and-const-api.json",
                "payloads/events-formats-const-mismatch-body.json", false),
            new Scenario("customers-strict-valid",
                "openapi/customers-strict-api.json",
                "payloads/customers-strict-valid-body.json", true),
            new Scenario("customers-strict-extra-root-key",
                "openapi/customers-strict-api.json",
                "payloads/customers-strict-extra-root-key-body.json", false),
            new Scenario("customers-strict-extra-nested-key",
                "openapi/customers-strict-api.json",
                "payloads/customers-strict-extra-nested-key-body.json", false),
            new Scenario("events-discriminator-paid-valid",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-paid-valid-body.json", true),
            new Scenario("events-discriminator-failed-valid",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-failed-valid-body.json", true),
            new Scenario("events-discriminator-paid-missing-paidat",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-paid-missing-paidat-body.json", false),
            new Scenario("events-discriminator-unknown-type",
                "openapi/events-discriminator-api.json",
                "payloads/events-discriminator-unknown-type-body.json", false),
            new Scenario("customers-kitchen-sink-valid",
                "openapi/customers-kitchen-sink-api.json",
                "payloads/customers-kitchen-sink-valid-body.json", true),
            new Scenario("customers-kitchen-sink-invalid",
                "openapi/customers-kitchen-sink-api.json",
                "payloads/customers-kitchen-sink-invalid-body.json", false),
            new Scenario("customers-query-params-valid",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-body.json",
                "payloads/customers-query-params-valid-params.json", true),
            new Scenario("customers-query-params-missing-tenant",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-body.json",
                "payloads/customers-query-params-missing-tenant-params.json", false),
            new Scenario("customers-query-params-tenant-not-in-enum",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-body.json",
                "payloads/customers-query-params-tenant-not-in-enum-params.json", false),
            new Scenario("customers-query-params-bad-page-size",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-body.json",
                "payloads/customers-query-params-bad-page-size-params.json", false),
            // Non-string query parameters are presence-only: values arrive as strings,
            // so a numeric type check would reject every well-formed request.
            new Scenario("customers-query-params-limit-not-numeric",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-body.json",
                "payloads/customers-query-params-limit-not-numeric-params.json", true),
            new Scenario("customers-query-params-bad-request-id",
                "openapi/customers-query-params-api.json",
                "payloads/customers-query-params-body.json",
                "payloads/customers-query-params-bad-request-id-params.json", false),
            new Scenario("customers-query-only-valid",
                "openapi/customers-query-only-api.json",
                "payloads/customers-query-only-body.json",
                "payloads/customers-query-only-valid-params.json", true),
            new Scenario("customers-query-only-missing-since",
                "openapi/customers-query-only-api.json",
                "payloads/customers-query-only-body.json",
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

    /** Splits generator output into one FEEL expression per {@code # METHOD /path} heading. */
    protected List<String> extractFeelExpressions(String output) {
        return Arrays.stream(output.split("(?m)^# .*$"))
            .map(String::strip)
            .filter(block -> !block.isEmpty())
            .toList();
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

    /** Engine results arrive as Scala collections; convert to plain Java maps/lists for AssertJ. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> asMap(Object value) {
        if (normalizeValue(value) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Value is not a map: " + value);
    }

    @SuppressWarnings("unchecked")
    protected List<Object> asList(Object value) {
        if (normalizeValue(value) instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException("Value is not a list: " + value);
    }

    private Object normalizeValue(Object value) {
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
