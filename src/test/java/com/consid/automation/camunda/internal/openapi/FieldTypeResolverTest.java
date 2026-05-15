package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.model.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldTypeResolverTest {

    private FieldTypeResolver resolver;
    private OpenAPI openAPI;

    @BeforeEach
    void setUp() {
        openAPI = new OpenAPI();
        resolver = new FieldTypeResolver(openAPI);
    }

    private static StringTypeInfo asString(FieldDescriptor d) {
        assertThat(d.typeInfo()).isInstanceOf(StringTypeInfo.class);
        return (StringTypeInfo) d.typeInfo();
    }

    private static NumberTypeInfo asNumber(FieldDescriptor d) {
        assertThat(d.typeInfo()).isInstanceOf(NumberTypeInfo.class);
        return (NumberTypeInfo) d.typeInfo();
    }

    private static ArrayTypeInfo asArray(FieldDescriptor d) {
        assertThat(d.typeInfo()).isInstanceOf(ArrayTypeInfo.class);
        return (ArrayTypeInfo) d.typeInfo();
    }

    private static ObjectTypeInfo asObject(FieldDescriptor d) {
        assertThat(d.typeInfo()).isInstanceOf(ObjectTypeInfo.class);
        return (ObjectTypeInfo) d.typeInfo();
    }

    @Test
    void test_resolve_string_does_map_to_plain_string_type_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("string"));
        assertThat(result.typeInfo()).isInstanceOf(StringTypeInfo.class);
        assertThat(asString(result).format()).isEqualTo(StringTypeInfo.StringFormat.PLAIN);
    }

    @Test
    void test_resolve_number_does_map_to_number_type_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("number"));
        assertThat(result.typeInfo()).isInstanceOf(NumberTypeInfo.class);
    }

    @Test
    void test_resolve_integer_does_map_to_number_type_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("integer"));
        assertThat(result.typeInfo()).isInstanceOf(NumberTypeInfo.class);
    }

    @Test
    void test_resolve_boolean_does_map_to_boolean_type_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("boolean"));
        assertThat(result.typeInfo()).isEqualTo(BooleanTypeInfo.INSTANCE);
    }

    @Test
    void test_resolve_array_does_map_to_array_type_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("array"));
        assertThat(result.typeInfo()).isInstanceOf(ArrayTypeInfo.class);
    }

    @Test
    void test_resolve_object_does_map_to_object_type_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("object"));
        assertThat(result.typeInfo()).isInstanceOf(ObjectTypeInfo.class);
    }

    @Test
    void test_resolve_unsupported_type_does_map_to_unknown_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("unsupported"));
        assertThat(result.typeInfo()).isEqualTo(UnknownTypeInfo.INSTANCE);
    }

    @Test
    void test_resolve_string_format_date_does_map_to_date_subtype_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("string").format("date"));
        assertThat(asString(result).format()).isEqualTo(StringTypeInfo.StringFormat.DATE);
    }

    @Test
    void test_resolve_string_format_date_time_does_map_to_date_time_subtype_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("string").format("date-time"));
        assertThat(asString(result).format()).isEqualTo(StringTypeInfo.StringFormat.DATE_TIME);
    }

    @Test
    void test_resolve_string_format_time_does_map_to_time_subtype_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("string").format("time"));
        assertThat(asString(result).format()).isEqualTo(StringTypeInfo.StringFormat.TIME);
    }

    @Test
    void test_resolve_string_format_uuid_does_remain_plain_with_pattern_as_expected() {
        // uuid maps to PLAIN string with an injected pattern, not to a separate subtype.
        FieldDescriptor result = resolver.resolve(new Schema<>().type("string").format("uuid"));
        assertThat(asString(result).format()).isEqualTo(StringTypeInfo.StringFormat.PLAIN);
        assertThat(asString(result).pattern()).startsWith("^[0-9a-fA-F]{8}");
    }

    @Test
    void test_resolve_null_schema_does_return_unknown_as_expected() {
        FieldDescriptor result = resolver.resolve(null);
        assertThat(result.typeInfo()).isEqualTo(UnknownTypeInfo.INSTANCE);
    }

    @Test
    void test_resolve_schema_without_type_but_with_properties_does_imply_object_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.addProperty("field1", new Schema<>().type("string"));
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.typeInfo())
            .as("A schema with properties but no explicit type should resolve to OBJECT")
            .isInstanceOf(ObjectTypeInfo.class);
    }

    @Test
    void test_resolve_schema_without_type_but_with_allOf_does_imply_object_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setAllOf(List.of(new Schema<>().type("object")));
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.typeInfo())
            .as("A schema using allOf is implicitly an object")
            .isInstanceOf(ObjectTypeInfo.class);
    }

    @Test
    void test_resolve_schema_without_type_but_with_oneOf_does_imply_object_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setOneOf(List.of(new Schema<>().type("object")));
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.typeInfo()).isInstanceOf(ObjectTypeInfo.class);
    }

    @Test
    void test_resolve_schema_without_type_but_with_anyOf_does_imply_object_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setAnyOf(List.of(new Schema<>().type("object")));
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.typeInfo()).isInstanceOf(ObjectTypeInfo.class);
    }

    @Test
    void test_resolve_schema_without_type_but_with_required_does_imply_object_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("field1"));
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.typeInfo())
            .as("A schema with only `required` should still resolve to OBJECT")
            .isInstanceOf(ObjectTypeInfo.class);
    }

    @Test
    void test_resolve_reference_does_follow_to_referenced_type_as_expected() {
        Schema<?> referenced = new Schema<>().type("boolean");
        openAPI.components(new io.swagger.v3.oas.models.Components().addSchemas("RefSchema", referenced));
        Schema<?> schema = new Schema<>();
        schema.set$ref("#/components/schemas/RefSchema");
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.typeInfo()).isEqualTo(BooleanTypeInfo.INSTANCE);
    }

    @Test
    void test_resolve_reference_without_components_does_throw_as_expected() {
        Schema<?> schema = new Schema<>().type("string");
        schema.set$ref("#/components/schemas/Missing");
        assertThatThrownBy(() -> resolver.resolveSchemaReference(schema))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("#/components/schemas/Missing");
    }

    @Test
    void test_resolve_null_reference_does_return_null_as_expected() {
        assertThat(resolver.resolveSchemaReference(null)).isNull();
    }

    @Test
    void test_resolve_enum_does_capture_values_as_expected() {
        Schema<String> schema = new Schema<>();
        schema.type("string");
        schema.setEnum(Arrays.asList("red", "green", "blue"));
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.enumValues()).containsExactly(
            new FeelString("red"), new FeelString("green"), new FeelString("blue"));
    }

    @Test
    void test_resolve_const_does_capture_as_single_value_enum_as_expected() {
        Schema<String> schema = new Schema<>();
        schema.type("string");
        schema._const("v1");
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.enumValues())
            .as("const should surface in the same channel as enum")
            .containsExactly(new FeelString("v1"));
    }

    @Test
    void test_resolve_enum_overrides_const_when_both_present_as_expected() {
        Schema<String> schema = new Schema<>();
        schema.type("string");
        schema.setEnum(Arrays.asList("a", "b"));
        schema._const("ignored");
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.enumValues()).containsExactly(new FeelString("a"), new FeelString("b"));
    }

    @Test
    void test_resolve_schema_without_enum_does_return_empty_enum_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("string"));
        assertThat(result.hasEnum()).isFalse();
    }

    @Test
    void test_resolve_openapi_3_0_nullable_flag_does_set_descriptor_nullable_as_expected() {
        Schema<?> schema = new Schema<>().type("string");
        schema.setNullable(Boolean.TRUE);
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.nullable()).isTrue();
    }

    @Test
    void test_resolve_openapi_3_1_type_array_with_null_does_set_descriptor_nullable_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setTypes(new java.util.LinkedHashSet<>(Arrays.asList("string", "null")));
        FieldDescriptor result = resolver.resolve(schema);
        assertThat(result.nullable()).isTrue();
        assertThat(result.typeInfo())
            .as("Primary type should be picked from the non-null entry")
            .isInstanceOf(StringTypeInfo.class);
    }

    @Test
    void test_resolve_schema_without_nullable_does_default_to_non_nullable_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("string"));
        assertThat(result.nullable()).isFalse();
    }

    @Test
    void test_resolve_array_without_constraints_does_default_to_none_as_expected() {
        FieldDescriptor result = resolver.resolve(new Schema<>().type("array"));
        ArrayTypeInfo info = asArray(result);
        assertThat(info.minItems()).isNull();
        assertThat(info.maxItems()).isNull();
        assertThat(info.items()).isNull();
    }

    @Test
    void test_resolve_array_min_items_does_capture_value_as_expected() {
        Schema<?> schema = new Schema<>().type("array");
        schema.setMinItems(2);
        ArrayTypeInfo info = asArray(resolver.resolve(schema));
        assertThat(info.minItems()).isEqualTo(2);
        assertThat(info.maxItems()).isNull();
    }

    @Test
    void test_resolve_array_min_items_zero_does_capture_value_as_expected() {
        // Explicit minItems: 0 is the "present but may be empty" signal.
        Schema<?> schema = new Schema<>().type("array");
        schema.setMinItems(0);
        assertThat(asArray(resolver.resolve(schema)).minItems()).isZero();
    }

    @Test
    void test_resolve_array_max_items_does_capture_value_as_expected() {
        Schema<?> schema = new Schema<>().type("array");
        schema.setMaxItems(5);
        ArrayTypeInfo info = asArray(resolver.resolve(schema));
        assertThat(info.maxItems()).isEqualTo(5);
        assertThat(info.minItems()).isNull();
    }

    @Test
    void test_resolve_array_min_and_max_items_does_capture_both_as_expected() {
        Schema<?> schema = new Schema<>().type("array");
        schema.setMinItems(1);
        schema.setMaxItems(3);
        ArrayTypeInfo info = asArray(resolver.resolve(schema));
        assertThat(info.minItems()).isEqualTo(1);
        assertThat(info.maxItems()).isEqualTo(3);
    }

    @Test
    void test_resolve_non_array_with_min_items_does_ignore_constraint_as_expected() {
        // minItems is array-only; on a string it has no meaning.
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinItems(2);
        assertThat(resolver.resolve(schema).typeInfo()).isInstanceOf(StringTypeInfo.class);
    }

    @Test
    void test_resolve_string_without_constraints_does_default_to_none_as_expected() {
        StringTypeInfo info = asString(resolver.resolve(new Schema<>().type("string")));
        assertThat(info.minLength()).isNull();
        assertThat(info.maxLength()).isNull();
        assertThat(info.pattern()).isNull();
    }

    @Test
    void test_resolve_string_min_length_does_capture_value_as_expected() {
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinLength(3);
        StringTypeInfo info = asString(resolver.resolve(schema));
        assertThat(info.minLength()).isEqualTo(3);
        assertThat(info.maxLength()).isNull();
        assertThat(info.pattern()).isNull();
    }

    @Test
    void test_resolve_string_min_length_zero_does_capture_value_as_expected() {
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinLength(0);
        assertThat(asString(resolver.resolve(schema)).minLength()).isZero();
    }

    @Test
    void test_resolve_string_max_length_does_capture_value_as_expected() {
        Schema<?> schema = new Schema<>().type("string");
        schema.setMaxLength(255);
        StringTypeInfo info = asString(resolver.resolve(schema));
        assertThat(info.maxLength()).isEqualTo(255);
        assertThat(info.minLength()).isNull();
    }

    @Test
    void test_resolve_string_pattern_does_capture_value_as_expected() {
        Schema<?> schema = new Schema<>().type("string");
        schema.setPattern("^[A-Z]{3}$");
        assertThat(asString(resolver.resolve(schema)).pattern()).isEqualTo("^[A-Z]{3}$");
    }

    @Test
    void test_resolve_string_combined_constraints_does_capture_all_as_expected() {
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinLength(2);
        schema.setMaxLength(10);
        schema.setPattern("^[a-z]+$");
        StringTypeInfo info = asString(resolver.resolve(schema));
        assertThat(info.minLength()).isEqualTo(2);
        assertThat(info.maxLength()).isEqualTo(10);
        assertThat(info.pattern()).isEqualTo("^[a-z]+$");
    }

    @Test
    void test_resolve_string_format_email_does_inject_canonical_pattern_as_expected() {
        StringTypeInfo info = asString(resolver.resolve(new Schema<>().type("string").format("email")));
        assertThat(info.pattern()).isNotNull().contains("@");
    }

    @Test
    void test_resolve_string_format_uuid_does_inject_canonical_pattern_as_expected() {
        StringTypeInfo info = asString(resolver.resolve(new Schema<>().type("string").format("uuid")));
        assertThat(info.pattern()).isNotNull().startsWith("^[0-9a-fA-F]{8}");
    }

    @Test
    void test_resolve_string_format_uri_does_inject_canonical_pattern_as_expected() {
        StringTypeInfo info = asString(resolver.resolve(new Schema<>().type("string").format("uri")));
        assertThat(info.pattern()).isNotNull();
    }

    @Test
    void test_resolve_string_format_url_alias_does_inject_uri_pattern_as_expected() {
        StringTypeInfo info = asString(resolver.resolve(new Schema<>().type("string").format("url")));
        assertThat(info.pattern()).isNotNull();
    }

    @Test
    void test_resolve_author_pattern_overrides_format_default_as_expected() {
        Schema<?> schema = new Schema<>().type("string").format("email");
        schema.setPattern("^custom$");
        assertThat(asString(resolver.resolve(schema)).pattern()).isEqualTo("^custom$");
    }

    @Test
    void test_resolve_non_string_with_pattern_does_ignore_constraint_as_expected() {
        // pattern is string-only; on a number it has no meaning.
        Schema<?> schema = new Schema<>().type("number");
        schema.setPattern("^[0-9]+$");
        assertThat(resolver.resolve(schema).typeInfo()).isInstanceOf(NumberTypeInfo.class);
    }

    @Test
    void test_resolve_date_string_with_min_length_does_attach_to_string_constraints_as_expected() {
        // DATE is a string subtype; length constraints still apply.
        Schema<?> schema = new Schema<>().type("string").format("date");
        schema.setMinLength(10);
        StringTypeInfo info = asString(resolver.resolve(schema));
        assertThat(info.format()).isEqualTo(StringTypeInfo.StringFormat.DATE);
        assertThat(info.minLength()).isEqualTo(10);
    }

    @Test
    void test_resolve_number_without_constraints_does_default_to_none_as_expected() {
        NumberTypeInfo info = asNumber(resolver.resolve(new Schema<>().type("number")));
        assertThat(info).isEqualTo(NumberTypeInfo.NONE);
    }

    @Test
    void test_resolve_number_minimum_does_capture_as_inclusive_lower_bound_as_expected() {
        Schema<?> schema = new Schema<>().type("number");
        schema.setMinimum(new BigDecimal("0"));
        NumberTypeInfo info = asNumber(resolver.resolve(schema));
        assertThat(info.minimum()).isEqualByComparingTo("0");
        assertThat(info.exclusiveMinimum()).isNull();
    }

    @Test
    void test_resolve_number_maximum_does_capture_as_inclusive_upper_bound_as_expected() {
        Schema<?> schema = new Schema<>().type("number");
        schema.setMaximum(new BigDecimal("100"));
        NumberTypeInfo info = asNumber(resolver.resolve(schema));
        assertThat(info.maximum()).isEqualByComparingTo("100");
        assertThat(info.exclusiveMaximum()).isNull();
    }

    @Test
    void test_resolve_number_exclusive_minimum_3_1_form_does_capture_as_exclusive_bound_as_expected() {
        Schema<?> schema = new Schema<>().type("number");
        schema.setExclusiveMinimumValue(new BigDecimal("0"));
        NumberTypeInfo info = asNumber(resolver.resolve(schema));
        assertThat(info.exclusiveMinimum()).isEqualByComparingTo("0");
        assertThat(info.minimum()).isNull();
    }

    @Test
    void test_resolve_number_exclusive_minimum_3_0_form_does_normalize_to_exclusive_bound_as_expected() {
        // OpenAPI 3.0: exclusiveMinimum is a boolean that promotes `minimum` to exclusive.
        Schema<?> schema = new Schema<>().type("number");
        schema.setMinimum(new BigDecimal("5"));
        schema.setExclusiveMinimum(Boolean.TRUE);
        NumberTypeInfo info = asNumber(resolver.resolve(schema));
        assertThat(info.exclusiveMinimum()).isEqualByComparingTo("5");
        assertThat(info.minimum()).isNull();
    }

    @Test
    void test_resolve_number_exclusive_maximum_3_1_form_does_capture_as_exclusive_bound_as_expected() {
        Schema<?> schema = new Schema<>().type("number");
        schema.setExclusiveMaximumValue(new BigDecimal("1"));
        NumberTypeInfo info = asNumber(resolver.resolve(schema));
        assertThat(info.exclusiveMaximum()).isEqualByComparingTo("1");
        assertThat(info.maximum()).isNull();
    }

    @Test
    void test_resolve_number_exclusive_maximum_3_0_form_does_normalize_to_exclusive_bound_as_expected() {
        Schema<?> schema = new Schema<>().type("number");
        schema.setMaximum(new BigDecimal("100"));
        schema.setExclusiveMaximum(Boolean.TRUE);
        NumberTypeInfo info = asNumber(resolver.resolve(schema));
        assertThat(info.exclusiveMaximum()).isEqualByComparingTo("100");
        assertThat(info.maximum()).isNull();
    }

    @Test
    void test_resolve_number_multiple_of_does_capture_value_as_expected() {
        Schema<?> schema = new Schema<>().type("number");
        schema.setMultipleOf(new BigDecimal("100"));
        assertThat(asNumber(resolver.resolve(schema)).multipleOf()).isEqualByComparingTo("100");
    }

    @Test
    void test_resolve_integer_with_number_constraints_does_still_capture_them_as_expected() {
        // `integer` resolves to NumberTypeInfo; range constraints still apply.
        Schema<?> schema = new Schema<>().type("integer");
        schema.setMinimum(new BigDecimal("18"));
        schema.setMaximum(new BigDecimal("120"));
        NumberTypeInfo info = asNumber(resolver.resolve(schema));
        assertThat(info.minimum()).isEqualByComparingTo("18");
        assertThat(info.maximum()).isEqualByComparingTo("120");
    }

    @Test
    void test_resolve_object_without_additional_properties_does_default_to_open_as_expected() {
        ObjectTypeInfo info = asObject(resolver.resolve(new Schema<>().type("object")));
        assertThat(info.isClosed()).isFalse();
    }

    @Test
    void test_resolve_object_with_additional_properties_false_does_capture_allowed_keys_as_expected() {
        Schema<?> schema = new Schema<>().type("object");
        schema.addProperty("id", new Schema<>().type("string"));
        schema.addProperty("name", new Schema<>().type("string"));
        schema.setAdditionalProperties(Boolean.FALSE);
        ObjectTypeInfo info = asObject(resolver.resolve(schema));
        assertThat(info.isClosed()).isTrue();
        assertThat(info.allowedKeys()).containsExactlyInAnyOrder("id", "name");
    }

    @Test
    void test_resolve_object_with_additional_properties_true_does_stay_open_as_expected() {
        Schema<?> schema = new Schema<>().type("object");
        schema.addProperty("id", new Schema<>().type("string"));
        schema.setAdditionalProperties(Boolean.TRUE);
        assertThat(asObject(resolver.resolve(schema)).isClosed()).isFalse();
    }

    @Test
    void test_resolve_non_object_with_additional_properties_does_ignore_as_expected() {
        // additionalProperties is object-only; on a string it's meaningless.
        Schema<?> schema = new Schema<>().type("string");
        schema.setAdditionalProperties(Boolean.FALSE);
        assertThat(resolver.resolve(schema).typeInfo()).isInstanceOf(StringTypeInfo.class);
    }

    @Test
    void test_resolve_non_number_with_minimum_does_ignore_constraint_as_expected() {
        // minimum is number-only; on a string it has no meaning.
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinimum(new BigDecimal("5"));
        assertThat(resolver.resolve(schema).typeInfo()).isInstanceOf(StringTypeInfo.class);
    }
}
