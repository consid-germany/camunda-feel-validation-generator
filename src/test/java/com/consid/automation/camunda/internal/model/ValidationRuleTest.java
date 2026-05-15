package com.consid.automation.camunda.internal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;


class ValidationRuleTest {

    @Test
    void test_factory_method_does_create_immutable_rule_as_expected() {
        // given
        String id = "field-invalid";
        String invalidExpression = "field=null";
        String fieldPath = "field";

        // when
        ValidationRule rule = ValidationRule.create(id, invalidExpression, fieldPath);

        // then
        assertThat(rule.id()).isEqualTo(id);
        assertThat(rule.invalidExpression()).isEqualTo(invalidExpression);
        assertThat(rule.fieldPath()).isEqualTo(fieldPath);
    }

    @Test
    void test_constructor_does_reject_null_values_as_expected() {
        // given
        String expression = "expr";
        String field = "field";

        // when // then
        assertThatThrownBy(() -> ValidationRule.create(null, expression, field))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id");

        assertThatThrownBy(() -> ValidationRule.create("id", null, field))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("invalidExpression");

        assertThatThrownBy(() -> ValidationRule.create("id", expression, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fieldPath");
    }
}
