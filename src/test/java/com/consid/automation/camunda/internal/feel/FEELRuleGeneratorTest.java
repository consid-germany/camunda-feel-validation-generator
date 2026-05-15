package com.consid.automation.camunda.internal.feel;

import com.consid.automation.camunda.internal.model.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FEELRuleGeneratorTest {

    @Test
    void test_create_rule_does_generate_field_rule_as_expected() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false);

        // when
        ValidationRule rule = generator.createRule("user.name", FieldDescriptor.of(StringTypeInfo.PLAIN));

        // then
        assertThat(rule.id()).isEqualTo("user.name-invalid");
        assertThat(rule.invalidExpression())
            .contains("req.user.name=null")
            .contains("instance of string");
        assertThat(rule.fieldPath()).isEqualTo("user.name");
    }

    @Test
    void test_render_basic_format_does_emit_activation_expression_as_expected() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(
            "# POST /users",
            List.of(
                ValidationRule.create("user-invalid", "req.user=null", "user"),
                ValidationRule.create("email-invalid", "req.email=null", "email")
            )
        );

        // when
        String output = generator.render(rulesByEndpoint);

        // then
        assertThat(output)
            .contains("# POST /users")
            .contains("req: request.body")
            .contains("rules:")
            .contains("{invalid: req.user=null}")
            .contains("{invalid: req.email=null}")
            .endsWith(".isValid");
    }

    @Test
    void test_render_response_format_does_emit_response_payload_as_expected() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(true);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(
            "# POST /users",
            List.of(ValidationRule.create("user-invalid", "req.user=null", "user"))
        );

        // when
        String output = generator.render(rulesByEndpoint);

        // then
        assertThat(output)
            .contains("# POST /users")
            .contains("field: \"user\"")
            .contains("body:")
            .contains("statusCode:")
            .contains("if isValid then");
    }

    @Test
    void test_render_does_include_multiple_endpoints_as_expected() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put("# POST /one", List.of(ValidationRule.create("a", "req.a=null", "a")));
        rulesByEndpoint.put("# PUT /two", List.of(ValidationRule.create("b", "req.b=null", "b")));

        // when
        String output = generator.render(rulesByEndpoint);

        // then
        assertThat(output)
            .contains("# POST /one")
            .contains("# PUT /two");
    }

    @Test
    void test_create_rule_for_presence_conditional_descriptor_does_emit_guarded_invalid_expression_as_expected() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        FieldDescriptor descriptor = new FieldDescriptor(
            StringTypeInfo.PLAIN, false, List.of(), List.of(Trigger.presence("shippingAddress")));

        // when
        ValidationRule rule = generator.createRule("shippingCarrier", descriptor);

        // then
        assertThat(rule.id()).isEqualTo("shippingCarrier-invalid");
        assertThat(rule.invalidExpression()).isEqualTo(
            "req.shippingAddress!=null and ("
                + "req.shippingCarrier=null"
                + " or not(req.shippingCarrier instance of string))");
    }

    @Test
    void test_create_rule_for_value_conditional_descriptor_does_emit_value_guard_as_expected() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        FieldDescriptor descriptor = new FieldDescriptor(
            StringTypeInfo.PLAIN, false, List.of(),
            List.of(Trigger.value("paymentMethod", List.of(new FeelString("card")))));

        // when
        ValidationRule rule = generator.createRule("cardNumber", descriptor);

        // then
        assertThat(rule.id()).isEqualTo("cardNumber-invalid");
        assertThat(rule.invalidExpression()).isEqualTo(
            "req.paymentMethod=\"card\" and ("
                + "req.cardNumber=null"
                + " or not(req.cardNumber instance of string))");
    }

    @Test
    void test_render_does_handle_empty_rules_as_expected() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put("# GET /empty", List.of());

        // when
        String output = generator.render(rulesByEndpoint);

        // then
        assertThat(output)
            .contains("rules: [")
            .contains("]");
    }
}
