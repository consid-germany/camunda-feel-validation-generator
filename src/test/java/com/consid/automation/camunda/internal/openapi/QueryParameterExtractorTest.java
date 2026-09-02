package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.Diagnostics;
import com.consid.automation.camunda.internal.model.FeelString;
import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.model.StringTypeInfo;
import com.consid.automation.camunda.internal.model.UnknownTypeInfo;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QueryParameterExtractor}. Query parameter values reach
 * the webhook as strings, so the extractor keeps string-family constraints and
 * reduces everything else to a presence check.
 */
class QueryParameterExtractorTest {

    @Test
    void test_extract_does_keep_string_constraints_and_enum() {
        // given
        Schema<?> schema = new StringSchema().pattern("^[a-z]+$").maxLength(10)._enum(List.of("acme", "globex"));
        Parameter tenant = queryParameter("tenant", schema);
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(new OpenAPI(), Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of(tenant));

        // then
        assertThat(result).containsOnlyKeys("tenant");
        FieldDescriptor descriptor = result.get("tenant");
        assertThat(descriptor.typeInfo()).isEqualTo(
            new StringTypeInfo(StringTypeInfo.StringFormat.PLAIN, null, 10, "^[a-z]+$"));
        assertThat(descriptor.enumValues()).containsExactly(new FeelString("acme"), new FeelString("globex"));
        assertThat(descriptor.nullable()).isFalse();
        assertThat(descriptor.isConditional()).isFalse();
    }

    @Test
    void test_extract_does_keep_temporal_string_format() {
        // given
        Parameter since = queryParameter("since", new Schema<>().type("string").format("date-time"));
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(new OpenAPI(), Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of(since));

        // then
        assertThat(result.get("since").typeInfo())
            .isEqualTo(new StringTypeInfo(StringTypeInfo.StringFormat.DATE_TIME, null, null, null));
    }

    @Test
    void test_extract_does_reduce_non_string_types_to_presence_only() {
        // given — integer with range + enum, boolean, array: none of these type
        // checks can hold against a string-valued query parameter.
        Schema<?> limitSchema = new IntegerSchema().minimum(java.math.BigDecimal.ONE)._enum(List.<Number>of(10, 20));
        Parameter limit = queryParameter("limit", limitSchema);
        Parameter verbose = queryParameter("verbose", new Schema<>().type("boolean"));
        Parameter tags = queryParameter("tags", new Schema<>().type("array").items(new Schema<>().type("string")));
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(new OpenAPI(), Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of(limit, verbose, tags));

        // then
        assertThat(result.values())
            .allSatisfy(descriptor -> {
                assertThat(descriptor.typeInfo()).isEqualTo(UnknownTypeInfo.INSTANCE);
                assertThat(descriptor.hasEnum()).isFalse();
                assertThat(descriptor.nullable()).isFalse();
            });
    }

    @Test
    void test_extract_does_ignore_nullable_because_required_means_present() {
        // given
        Parameter tenant = queryParameter("tenant", new Schema<>().type("string").nullable(true));
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(new OpenAPI(), Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of(tenant));

        // then
        assertThat(result.get("tenant").nullable()).isFalse();
    }

    @Test
    void test_extract_does_treat_missing_schema_as_presence_only() {
        // given — `content`-style parameters carry no direct schema
        Parameter filter = new Parameter().name("filter").in("query").required(true);
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(new OpenAPI(), Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of(filter));

        // then
        assertThat(result.get("filter")).isEqualTo(FieldDescriptor.of(UnknownTypeInfo.INSTANCE));
    }

    @Test
    void test_extract_does_resolve_schema_ref() {
        // given
        Schema<?> tenantSchema = new Schema<>().type("string").minLength(2);
        OpenAPI openAPI = new OpenAPI().components(new Components().addSchemas("TenantId", tenantSchema));
        Parameter tenant = queryParameter("tenant", new Schema<>().$ref("#/components/schemas/TenantId"));
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(openAPI, Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of(tenant));

        // then
        assertThat(result.get("tenant").typeInfo())
            .isEqualTo(new StringTypeInfo(StringTypeInfo.StringFormat.PLAIN, 2, null, null));
    }

    @Test
    void test_extract_does_sort_parameters_by_name() {
        // given
        Parameter zeta = queryParameter("zeta", new Schema<>().type("string"));
        Parameter alpha = queryParameter("alpha", new Schema<>().type("string"));
        Parameter mid = queryParameter("mid", new Schema<>().type("string"));
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(new OpenAPI(), Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of(zeta, alpha, mid));

        // then
        assertThat(result.keySet()).containsExactly("alpha", "mid", "zeta");
    }

    @Test
    void test_extract_does_return_empty_map_for_no_parameters() {
        // given
        QueryParameterExtractor extractor = new QueryParameterExtractor(new FieldTypeResolver(new OpenAPI(), Diagnostics.NOOP));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(List.of());

        // then
        assertThat(result).isEmpty();
    }

    private static Parameter queryParameter(String name, Schema<?> schema) {
        return new Parameter().name(name).in("query").required(true).schema(schema);
    }
}
