package com.consid.automation.camunda.internal.feel;

import com.consid.automation.camunda.internal.model.Endpoint;
import com.consid.automation.camunda.internal.model.FeelString;
import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.model.StringTypeInfo;
import com.consid.automation.camunda.internal.model.Trigger;
import com.consid.automation.camunda.internal.model.UnknownTypeInfo;
import com.consid.automation.camunda.internal.model.ValidationRule;
import com.consid.automation.camunda.internal.model.ValidationRule.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FEELRuleGeneratorTest {

    @Test
    void test_create_rule_does_generate_field_rule() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);

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
    void test_render_basic_format_does_emit_activation_expression() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);
        Map<Endpoint, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(
            new Endpoint("POST", "/users"),
            List.of(
                new ValidationRule("user-invalid", "req.user=null", "user", InputSource.BODY),
                new ValidationRule("email-invalid", "req.email=null", "email", InputSource.BODY)
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
    void test_render_response_format_does_emit_response_payload() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(true, 201, 400);
        Map<Endpoint, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(
            new Endpoint("POST", "/users"),
            List.of(new ValidationRule("user-invalid", "req.user=null", "user", InputSource.BODY))
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
    void test_render_does_include_multiple_endpoints() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);
        Map<Endpoint, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(new Endpoint("POST", "/one"), List.of(new ValidationRule("a", "req.a=null", "a", InputSource.BODY)));
        rulesByEndpoint.put(new Endpoint("PUT", "/two"), List.of(new ValidationRule("b", "req.b=null", "b", InputSource.BODY)));

        // when
        String output = generator.render(rulesByEndpoint);

        // then
        assertThat(output)
            .contains("# POST /one")
            .contains("# PUT /two");
    }

    @Test
    void test_create_rule_for_presence_conditional_descriptor_does_emit_guarded_invalid_expression() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);
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
    void test_create_rule_for_value_conditional_descriptor_does_emit_value_guard() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);
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
    void test_create_query_parameter_rule_does_reference_request_params() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);
        FieldDescriptor descriptor = new FieldDescriptor(
            StringTypeInfo.PLAIN, false, List.of(new FeelString("acme"), new FeelString("globex")), List.of());

        // when
        ValidationRule rule = generator.createQueryParameterRule("tenant", descriptor);

        // then
        assertThat(rule.id()).isEqualTo("params.tenant-invalid");
        assertThat(rule.fieldPath()).isEqualTo("params.tenant");
        assertThat(rule.invalidExpression()).isEqualTo(
            "request.params.tenant=null"
                + " or not(request.params.tenant instance of string)"
                + " or not(request.params.tenant in (\"acme\", \"globex\"))");
    }

    @Test
    void test_create_query_parameter_rule_does_emit_presence_only_for_unknown_type() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);

        // when
        ValidationRule rule = generator.createQueryParameterRule("limit", FieldDescriptor.of(UnknownTypeInfo.INSTANCE));

        // then
        assertThat(rule.invalidExpression()).isEqualTo("request.params.limit=null");
    }

    @Test
    void test_create_query_parameter_rule_does_use_get_value_for_non_identifier_names() {
        // given — `page-size` would parse as a subtraction if emitted as a bare path
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);

        // when
        ValidationRule rule = generator.createQueryParameterRule("page-size", FieldDescriptor.of(StringTypeInfo.PLAIN));

        // then
        assertThat(rule.id()).isEqualTo("params.page-size-invalid");
        assertThat(rule.fieldPath()).isEqualTo("params.page-size");
        assertThat(rule.invalidExpression()).isEqualTo(
            "get value(request.params, \"page-size\")=null"
                + " or not(get value(request.params, \"page-size\") instance of string)");
    }

    @Test
    void test_create_query_parameter_rule_does_use_get_value_for_feel_keywords() {
        // given — `in` is a FEEL keyword and cannot follow a dot as a path segment
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);

        // when
        ValidationRule rule = generator.createQueryParameterRule("in", FieldDescriptor.of(UnknownTypeInfo.INSTANCE));

        // then
        assertThat(rule.invalidExpression()).isEqualTo("get value(request.params, \"in\")=null");
    }

    /**
     * Names the bare-path branch must not take: not a FEEL identifier (leading
     * digit, non-ASCII letter, brackets, whitespace) or carrying characters that
     * need escaping inside the FEEL string literal.
     */
    static Stream<Arguments> awkwardParameterNames() {
        return Stream.of(
            Arguments.of("2fa", "get value(request.params, \"2fa\")"),
            Arguments.of("größe", "get value(request.params, \"größe\")"),
            Arguments.of("filter[status]", "get value(request.params, \"filter[status]\")"),
            Arguments.of("page size", "get value(request.params, \"page size\")"),
            Arguments.of("say\"hi\\there", "get value(request.params, \"say\\\"hi\\\\there\")")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("awkwardParameterNames")
    void test_create_query_parameter_rule_does_quote_and_escape_awkward_names(String name,
                                                                                            String accessor) {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);

        // when
        ValidationRule rule = generator.createQueryParameterRule(name, FieldDescriptor.of(UnknownTypeInfo.INSTANCE));

        // then
        assertThat(rule.invalidExpression()).isEqualTo(accessor + "=null");
        assertThat(rule.fieldPath()).isEqualTo("params." + name);
    }

    @Test
    void test_render_does_bind_body_alias_only_when_a_rule_reads_the_body() {
        // given — one endpoint with a body rule, one with only a query-parameter rule
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);
        Map<Endpoint, List<ValidationRule>> rulesByEndpoint = new LinkedHashMap<>();
        rulesByEndpoint.put(new Endpoint("POST", "/with-body"), List.of(
            generator.createRule("name", FieldDescriptor.of(StringTypeInfo.PLAIN)),
            generator.createQueryParameterRule("tenant", FieldDescriptor.of(UnknownTypeInfo.INSTANCE))));
        rulesByEndpoint.put(new Endpoint("POST", "/query-only"), List.of(
            generator.createQueryParameterRule("since", FieldDescriptor.of(UnknownTypeInfo.INSTANCE))));

        // when
        String output = generator.render(rulesByEndpoint);

        // then
        String[] blocks = output.split("\n\n");
        assertThat(blocks[0]).startsWith("# POST /with-body\n{\n  req: request.body,\n  rules: [");
        assertThat(blocks[1]).startsWith("# POST /query-only\n{\n  rules: [");
    }

    @Test
    void test_render_response_format_does_quote_rule_identifiers_as_feel_strings() {
        // given — a field path containing a quote must not break the rule line
        FEELRuleGenerator generator = new FEELRuleGenerator(true, 201, 400);
        ValidationRule rule = generator.createQueryParameterRule("say\"hi", FieldDescriptor.of(UnknownTypeInfo.INSTANCE));

        // when
        String output = generator.render(Map.of(new Endpoint("POST", "/x"), List.of(rule)));

        // then
        assertThat(output).contains("{ id: \"params.say\\\"hi-invalid\", field: \"params.say\\\"hi\", invalid: ");
    }

    @Test
    void test_render_does_handle_empty_rules() {
        // given
        FEELRuleGenerator generator = new FEELRuleGenerator(false, 201, 400);
        Map<Endpoint, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(new Endpoint("GET", "/empty"), List.of());

        // when
        String output = generator.render(rulesByEndpoint);

        // then
        assertThat(output)
            .contains("rules: [")
            .contains("]");
    }
}
