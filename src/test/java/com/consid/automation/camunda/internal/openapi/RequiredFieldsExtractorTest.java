package com.consid.automation.camunda.internal.openapi;

import com.consid.automation.camunda.internal.model.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RequiredFieldsExtractor.
 */
class RequiredFieldsExtractorTest {

    private RequiredFieldsExtractor extractor;

    @BeforeEach
    void setUp() {
        OpenAPI openAPI = new OpenAPI();
        FieldTypeResolver typeResolver = new FieldTypeResolver(openAPI);
        extractor = new RequiredFieldsExtractor(typeResolver);
    }

    @Test
    void test_extract_simple_required_field_does_capture_entry_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("username"));
        schema.addProperty("username", new Schema<>().type("string"));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result).containsEntry("username", FieldDescriptor.of(StringTypeInfo.PLAIN));
    }

    @Test
    void test_extract_required_fields_does_ignore_optional_fields_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("username"));
        schema.addProperty("username", new Schema<>().type("string"));
        schema.addProperty("optional", new Schema<>().type("string"));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result)
            .hasSize(1)
            .containsEntry("username", FieldDescriptor.of(StringTypeInfo.PLAIN));
    }

    @Test
    void test_extract_does_emit_full_dot_path_for_nested_fields_as_expected() {
        // given
        Schema<?> nestedSchema = new Schema<>();
        nestedSchema.setRequired(Arrays.asList("content"));
        nestedSchema.addProperty("content", new Schema<>().type("string"));
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("data"));
        schema.addProperty("data", nestedSchema);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result)
            .containsEntry("data", FieldDescriptor.of(ObjectTypeInfo.OPEN))
            .containsEntry("data.content", FieldDescriptor.of(StringTypeInfo.PLAIN));
    }

    @Test
    void test_extract_allof_does_merge_required_fields_from_each_branch_as_expected() {
        // given
        Schema<?> baseSchema = new Schema<>();
        baseSchema.setRequired(Arrays.asList("id"));
        baseSchema.addProperty("id", new Schema<>().type("string"));
        Schema<?> dataSchema = new Schema<>();
        dataSchema.setRequired(Arrays.asList("content"));
        dataSchema.addProperty("content", new Schema<>().type("string"));
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setAllOf(Arrays.asList(baseSchema, dataSchema));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(composedSchema).requiredFields();

        // then
        assertThat(result)
            .containsEntry("id", FieldDescriptor.of(StringTypeInfo.PLAIN))
            .containsEntry("content", FieldDescriptor.of(StringTypeInfo.PLAIN));
    }

    @Test
    void test_extract_oneof_does_collect_required_fields_from_each_branch_as_expected() {
        // given
        Schema<?> firstOption = new Schema<>();
        firstOption.setRequired(Arrays.asList("name"));
        firstOption.addProperty("name", new Schema<>().type("string"));
        Schema<?> secondOption = new Schema<>();
        secondOption.setRequired(Arrays.asList("email"));
        secondOption.addProperty("email", new Schema<>().type("string"));
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setOneOf(Arrays.asList(firstOption, secondOption));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(composedSchema).requiredFields();

        // then
        assertThat(result)
            .containsEntry("name", FieldDescriptor.of(StringTypeInfo.PLAIN))
            .containsEntry("email", FieldDescriptor.of(StringTypeInfo.PLAIN));
    }

    @Test
    void test_extract_anyof_does_collect_required_fields_from_each_branch_as_expected() {
        // given
        Schema<?> firstOption = new Schema<>();
        firstOption.setRequired(Arrays.asList("username"));
        firstOption.addProperty("username", new Schema<>().type("string"));
        Schema<?> secondOption = new Schema<>();
        secondOption.setRequired(Arrays.asList("userId"));
        secondOption.addProperty("userId", new Schema<>().type("string"));
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setAnyOf(Arrays.asList(firstOption, secondOption));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(composedSchema).requiredFields();

        // then
        assertThat(result)
            .containsEntry("username", FieldDescriptor.of(StringTypeInfo.PLAIN))
            .containsEntry("userId", FieldDescriptor.of(StringTypeInfo.PLAIN));
    }

    @Test
    void test_extract_empty_schema_does_return_empty_map_as_expected() {
        // when
        Map<String, FieldDescriptor> result = extractor.extract(new Schema<>()).requiredFields();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_extract_null_schema_does_return_empty_map_as_expected() {
        // when
        Map<String, FieldDescriptor> result = extractor.extract(null).requiredFields();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_extract_dependent_required_does_mark_field_as_conditional_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.addProperty("shippingAddress", new Schema<>().type("object"));
        schema.addProperty("shippingCarrier", new Schema<>().type("string"));
        schema.setDependentRequired(Map.of("shippingAddress", List.of("shippingCarrier")));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        FieldDescriptor carrier = result.get("shippingCarrier");
        assertThat(carrier).isNotNull();
        assertThat(carrier.typeInfo()).isInstanceOf(StringTypeInfo.class);
        assertThat(carrier.isConditional()).isTrue();
        assertThat(carrier.dependsOn()).containsExactly(Trigger.presence("shippingAddress"));
    }

    @Test
    void test_extract_dependent_required_does_not_downgrade_unconditional_required_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setRequired(List.of("shippingCarrier"));
        schema.addProperty("shippingAddress", new Schema<>().type("object"));
        schema.addProperty("shippingCarrier", new Schema<>().type("string"));
        schema.setDependentRequired(Map.of("shippingAddress", List.of("shippingCarrier")));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result.get("shippingCarrier").isConditional())
            .as("Field already required unconditionally should not become conditional")
            .isFalse();
    }

    @Test
    void test_extract_dependent_required_does_or_merge_multiple_triggers_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.addProperty("a", new Schema<>().type("string"));
        schema.addProperty("b", new Schema<>().type("string"));
        schema.addProperty("c", new Schema<>().type("string"));
        schema.setDependentRequired(Map.of(
            "a", List.of("c"),
            "b", List.of("c")
        ));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result.get("c").dependsOn())
            .as("c should be triggered by either a or b being present")
            .containsExactlyInAnyOrder(Trigger.presence("a"), Trigger.presence("b"));
    }

    @Test
    void test_extract_if_then_with_const_does_mark_field_with_value_trigger_as_expected() {
        // given — "if paymentMethod = 'card', then cardNumber is required"
        Schema<?> ifSchema = new Schema<>();
        ifSchema.addProperty("paymentMethod", new Schema<>()._const("card"));
        ifSchema.setRequired(List.of("paymentMethod"));
        Schema<?> thenSchema = new Schema<>();
        thenSchema.setRequired(List.of("cardNumber"));

        Schema<?> schema = new Schema<>();
        schema.addProperty("paymentMethod", new Schema<>().type("string"));
        schema.addProperty("cardNumber", new Schema<>().type("string"));
        schema.setIf(ifSchema);
        schema.setThen(thenSchema);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        FieldDescriptor cardNumber = result.get("cardNumber");
        assertThat(cardNumber).isNotNull();
        assertThat(cardNumber.isConditional()).isTrue();
        assertThat(cardNumber.dependsOn())
            .containsExactly(Trigger.value("paymentMethod", List.of(new FeelString("card"))));
    }

    @Test
    void test_extract_if_then_with_enum_does_mark_field_with_multi_value_trigger_as_expected() {
        // given — "if tier in [gold, platinum], then discountCode is required"
        Schema<?> ifSchema = new Schema<>();
        ifSchema.addProperty("tier", new Schema<>()._enum(List.of("gold", "platinum")));
        ifSchema.setRequired(List.of("tier"));
        Schema<?> thenSchema = new Schema<>();
        thenSchema.setRequired(List.of("discountCode"));

        Schema<?> schema = new Schema<>();
        schema.addProperty("tier", new Schema<>().type("string"));
        schema.addProperty("discountCode", new Schema<>().type("string"));
        schema.setIf(ifSchema);
        schema.setThen(thenSchema);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result.get("discountCode").dependsOn())
            .containsExactly(Trigger.value("tier", List.of(new FeelString("gold"), new FeelString("platinum"))));
    }

    @Test
    void test_extract_if_then_with_unsupported_shape_does_skip_silently_as_expected() {
        // given — multi-property if is outside the supported subset
        Schema<?> ifSchema = new Schema<>();
        ifSchema.addProperty("a", new Schema<>()._const("x"));
        ifSchema.addProperty("b", new Schema<>()._const("y"));
        ifSchema.setRequired(List.of("a", "b"));
        Schema<?> thenSchema = new Schema<>();
        thenSchema.setRequired(List.of("c"));

        Schema<?> schema = new Schema<>();
        schema.addProperty("a", new Schema<>().type("string"));
        schema.addProperty("b", new Schema<>().type("string"));
        schema.addProperty("c", new Schema<>().type("string"));
        schema.setIf(ifSchema);
        schema.setThen(thenSchema);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result)
            .as("Unsupported if-shapes should not introduce conditional rules")
            .doesNotContainKey("c");
    }

    @Test
    void test_extract_conditional_nested_required_propagates_trigger_to_inner_fields_as_expected() {
        // given — when parent is conditional on a value-trigger, its inner required fields inherit the same trigger
        Schema<?> deliverySchema = new Schema<>().type("object");
        deliverySchema.setRequired(List.of("address"));
        deliverySchema.addProperty("address", new Schema<>().type("string"));

        Schema<?> ifSchema = new Schema<>();
        ifSchema.addProperty("needsDelivery", new Schema<>()._const(Boolean.TRUE));
        ifSchema.setRequired(List.of("needsDelivery"));
        Schema<?> thenSchema = new Schema<>();
        thenSchema.setRequired(List.of("delivery"));

        Schema<?> root = new Schema<>().type("object");
        root.addProperty("needsDelivery", new Schema<>().type("boolean"));
        root.addProperty("delivery", deliverySchema);
        root.setIf(ifSchema);
        root.setThen(thenSchema);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(root).requiredFields();

        // then
        Trigger expected = Trigger.value("needsDelivery", List.of(new FeelBoolean(true)));
        assertThat(result.get("delivery").dependsOn()).containsExactly(expected);
        assertThat(result.get("delivery.address").dependsOn())
            .as("Inner required fields of a conditionally-required parent must inherit its trigger")
            .containsExactly(expected);
    }

    @Test
    void test_extract_plain_optional_nested_object_does_omit_inner_required_fields_as_expected() {
        // given — profile is OPTIONAL at root (not in required, no triggers); inner required should not leak out
        Schema<?> profile = new Schema<>().type("object");
        profile.setRequired(List.of("bio"));
        profile.addProperty("bio", new Schema<>().type("string"));

        Schema<?> root = new Schema<>().type("object");
        root.addProperty("profile", profile);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(root).requiredFields();

        // then
        assertThat(result)
            .as("Optional nested objects should not emit any rules for their inner required fields")
            .doesNotContainKey("profile")
            .doesNotContainKey("profile.bio");
    }

    @Test
    void test_extract_self_referential_schema_does_terminate_at_cycle_as_expected() {
        // given — a node that references itself; without cycle detection this would recurse forever
        Schema<?> node = new Schema<>();
        node.type("object");
        node.setRequired(List.of("value", "next"));
        node.addProperty("value", new Schema<>().type("string"));
        node.addProperty("next", node);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(node).requiredFields();

        // then
        assertThat(result)
            .as("Cycle detection should yield the top-level fields and stop before recursing into the self-reference")
            .containsOnlyKeys("value", "next");
    }

    @Test
    void test_extract_oneof_with_discriminator_does_make_branch_fields_conditional_on_value_as_expected() {
        // given — typical webhook event shape: a `type` discriminator selects one of two payload schemas
        io.swagger.v3.oas.models.OpenAPI openAPI = new io.swagger.v3.oas.models.OpenAPI();
        io.swagger.v3.oas.models.Components components = new io.swagger.v3.oas.models.Components();

        Schema<?> invoicePaid = new Schema<>().type("object");
        invoicePaid.setRequired(List.of("type", "paidAt"));
        invoicePaid.addProperty("type", new Schema<>().type("string"));
        invoicePaid.addProperty("paidAt", new Schema<>().type("string"));
        components.addSchemas("InvoicePaid", invoicePaid);

        Schema<?> invoiceFailed = new Schema<>().type("object");
        invoiceFailed.setRequired(List.of("type", "failureReason"));
        invoiceFailed.addProperty("type", new Schema<>().type("string"));
        invoiceFailed.addProperty("failureReason", new Schema<>().type("string"));
        components.addSchemas("InvoiceFailed", invoiceFailed);
        openAPI.setComponents(components);

        Schema<?> paidRef = new Schema<>(); paidRef.set$ref("#/components/schemas/InvoicePaid");
        Schema<?> failedRef = new Schema<>(); failedRef.set$ref("#/components/schemas/InvoiceFailed");
        Schema<?> root = new Schema<>().type("object");
        root.setOneOf(Arrays.asList(paidRef, failedRef));
        io.swagger.v3.oas.models.media.Discriminator d = new io.swagger.v3.oas.models.media.Discriminator();
        d.setPropertyName("type");
        d.setMapping(Map.of(
            "invoice.paid", "#/components/schemas/InvoicePaid",
            "invoice.failed", "#/components/schemas/InvoiceFailed"));
        root.setDiscriminator(d);

        RequiredFieldsExtractor disc = new RequiredFieldsExtractor(new FieldTypeResolver(openAPI));

        // when
        Map<String, FieldDescriptor> result = disc.extract(root).requiredFields();

        // then — paidAt only required when type="invoice.paid"; failureReason only when type="invoice.failed"
        assertThat(result).containsKeys("type", "paidAt", "failureReason");

        FieldDescriptor paidAt = result.get("paidAt");
        assertThat(paidAt.isConditional()).isTrue();
        assertThat(paidAt.dependsOn())
            .containsExactly(Trigger.value("type", List.of(new FeelString("invoice.paid"))));

        FieldDescriptor failureReason = result.get("failureReason");
        assertThat(failureReason.isConditional()).isTrue();
        assertThat(failureReason.dependsOn())
            .containsExactly(Trigger.value("type", List.of(new FeelString("invoice.failed"))));
    }

    @Test
    void test_extract_oneof_with_discriminator_does_make_discriminator_field_unconditionally_required_as_expected() {
        // given — the discriminator property itself must always be present so we know which branch applies
        io.swagger.v3.oas.models.OpenAPI openAPI = new io.swagger.v3.oas.models.OpenAPI();
        io.swagger.v3.oas.models.Components components = new io.swagger.v3.oas.models.Components();

        Schema<?> a = new Schema<>().type("object");
        a.setRequired(List.of("type"));
        a.addProperty("type", new Schema<>().type("string"));
        components.addSchemas("A", a);
        Schema<?> b = new Schema<>().type("object");
        b.setRequired(List.of("type"));
        b.addProperty("type", new Schema<>().type("string"));
        components.addSchemas("B", b);
        openAPI.setComponents(components);

        Schema<?> aRef = new Schema<>(); aRef.set$ref("#/components/schemas/A");
        Schema<?> bRef = new Schema<>(); bRef.set$ref("#/components/schemas/B");
        Schema<?> root = new Schema<>().type("object");
        root.setOneOf(Arrays.asList(aRef, bRef));
        io.swagger.v3.oas.models.media.Discriminator d = new io.swagger.v3.oas.models.media.Discriminator();
        d.setPropertyName("type");
        d.setMapping(Map.of("a-event", "#/components/schemas/A", "b-event", "#/components/schemas/B"));
        root.setDiscriminator(d);

        RequiredFieldsExtractor disc = new RequiredFieldsExtractor(new FieldTypeResolver(openAPI));

        // when
        Map<String, FieldDescriptor> result = disc.extract(root).requiredFields();

        // then
        FieldDescriptor type = result.get("type");
        assertThat(type).isNotNull();
        assertThat(type.isConditional())
            .as("Discriminator field must always be required — otherwise a missing `type` silently disables all branch checks")
            .isFalse();
        assertThat(type.enumValues())
            .as("Discriminator enum should pin the allowed branch values for the type field")
            .containsExactlyInAnyOrder(new FeelString("a-event"), new FeelString("b-event"));
    }

    @Test
    void test_extract_oneof_without_discriminator_does_still_union_merge_as_expected() {
        // given — backward compatibility: without discriminator, fall back to today's union-merge
        Schema<?> first = new Schema<>().type("object");
        first.setRequired(List.of("a"));
        first.addProperty("a", new Schema<>().type("string"));
        Schema<?> second = new Schema<>().type("object");
        second.setRequired(List.of("b"));
        second.addProperty("b", new Schema<>().type("string"));
        Schema<?> root = new Schema<>();
        root.setOneOf(Arrays.asList(first, second));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(root).requiredFields();

        // then
        assertThat(result.get("a").isConditional()).isFalse();
        assertThat(result.get("b").isConditional()).isFalse();
    }

    @Test
    void test_extract_shared_component_does_expand_at_every_reference_as_expected() {
        // given
        Schema<?> sharedAddress = new Schema<>();
        sharedAddress.setRequired(Arrays.asList("city", "street"));
        sharedAddress.addProperty("city", new Schema<>().type("string"));
        sharedAddress.addProperty("street", new Schema<>().type("string"));
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("billingAddress", "shippingAddress"));
        schema.addProperty("billingAddress", sharedAddress);
        schema.addProperty("shippingAddress", sharedAddress);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema).requiredFields();

        // then
        assertThat(result).containsKeys(
            "billingAddress",
            "shippingAddress",
            "billingAddress.city",
            "billingAddress.street",
            "shippingAddress.city",
            "shippingAddress.street"
        );
    }
}
