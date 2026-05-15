package com.consid.automation.camunda.internal;

import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.openapi.FieldTypeResolver;
import com.consid.automation.camunda.internal.openapi.RequiredFieldsExtractor;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the generator emits visible warnings — rather than silently
 * skipping — when it encounters author-detected-but-unsupported constructs.
 * Each warning is formatted with the schema location plus a hint on how to fix.
 */
class DiagnosticsTest {

    private final List<String> captured = new ArrayList<>();
    private final Diagnostics diagnostics = new Diagnostics(captured::add);

    @Test
    void test_unsupported_if_then_predicate_does_emit_warning_as_expected() {
        // given — `if` with two-property predicate is outside the supported subset
        Schema<?> ifSchema = new Schema<>();
        ifSchema.addProperty("a", new Schema<>()._const("x"));
        ifSchema.addProperty("b", new Schema<>()._const("y"));
        ifSchema.setRequired(List.of("a", "b"));
        Schema<?> thenSchema = new Schema<>();
        thenSchema.setRequired(List.of("c"));

        Schema<?> root = new Schema<>();
        root.addProperty("a", new Schema<>().type("string"));
        root.addProperty("b", new Schema<>().type("string"));
        root.addProperty("c", new Schema<>().type("string"));
        root.setIf(ifSchema);
        root.setThen(thenSchema);

        OpenAPI openAPI = new OpenAPI();
        RequiredFieldsExtractor extractor = new RequiredFieldsExtractor(
            new FieldTypeResolver(openAPI, diagnostics), diagnostics);

        // when
        extractor.extract(root);

        // then
        assertThat(captured)
            .as("An unsupported if/then shape should produce a single visible warning, not be silent")
            .hasSize(1);
        assertThat(captured.get(0))
            .contains("(root)")
            .contains("if/then predicate shape not supported")
            .contains("single-property predicate using `const` or `enum`");
    }

    @Test
    void test_oneof_without_discriminator_mapping_does_emit_warning_as_expected() {
        // given — oneOf with no discriminator falls back to union-merge
        Schema<?> first = new Schema<>().type("object");
        first.setRequired(List.of("a"));
        first.addProperty("a", new Schema<>().type("string"));
        Schema<?> second = new Schema<>().type("object");
        second.setRequired(List.of("b"));
        second.addProperty("b", new Schema<>().type("string"));
        Schema<?> root = new Schema<>();
        root.setOneOf(List.of(first, second));

        OpenAPI openAPI = new OpenAPI();
        RequiredFieldsExtractor extractor = new RequiredFieldsExtractor(
            new FieldTypeResolver(openAPI, diagnostics), diagnostics);

        // when
        Map<String, FieldDescriptor> required = extractor.extract(root).requiredFields();

        // then — union-merge still happens (backward compat), but the user is warned
        assertThat(required).containsKeys("a", "b");
        assertThat(captured)
            .as("oneOf without discriminator should warn that union-merge is stricter than the spec implies")
            .hasSize(1);
        assertThat(captured.get(0))
            .contains("oneOf without `discriminator.mapping`")
            .contains("union-merge")
            .contains("add a `discriminator`");
    }

    @Test
    void test_schema_form_additional_properties_does_emit_warning_as_expected() {
        // given — additionalProperties is a Schema, not a boolean
        Schema<?> schema = new Schema<>().type("object");
        schema.addProperty("name", new Schema<>().type("string"));
        schema.setAdditionalProperties(new Schema<>().type("string"));

        OpenAPI openAPI = new OpenAPI();
        FieldTypeResolver resolver = new FieldTypeResolver(openAPI, diagnostics);

        // when
        resolver.resolve(schema);

        // then
        assertThat(captured)
            .as("Schema-form additionalProperties should not be silently ignored")
            .hasSize(1);
        assertThat(captured.get(0))
            .contains("schema-form `additionalProperties` is not supported")
            .contains("only `additionalProperties: false` is honored");
    }

    @Test
    void test_supported_if_then_does_not_emit_warning_as_expected() {
        // given — a supported single-property if/then; no warning expected
        Schema<?> ifSchema = new Schema<>();
        ifSchema.addProperty("paymentMethod", new Schema<>()._const("card"));
        ifSchema.setRequired(List.of("paymentMethod"));
        Schema<?> thenSchema = new Schema<>();
        thenSchema.setRequired(List.of("cardNumber"));
        Schema<?> root = new Schema<>();
        root.addProperty("paymentMethod", new Schema<>().type("string"));
        root.addProperty("cardNumber", new Schema<>().type("string"));
        root.setIf(ifSchema);
        root.setThen(thenSchema);

        OpenAPI openAPI = new OpenAPI();
        RequiredFieldsExtractor extractor = new RequiredFieldsExtractor(
            new FieldTypeResolver(openAPI, diagnostics), diagnostics);

        // when
        extractor.extract(root);

        // then
        assertThat(captured)
            .as("A spec the generator fully supports should produce no warnings")
            .isEmpty();
    }
}
