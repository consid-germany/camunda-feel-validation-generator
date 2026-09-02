package com.consid.automation.camunda.internal.model;

import com.consid.automation.camunda.internal.model.ValidationRule.InputSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationRuleTest {

    @Test
    void test_constructor_does_reject_null_components() {
        assertThatThrownBy(() -> new ValidationRule(null, "expr", "field", InputSource.BODY))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("id");
        assertThatThrownBy(() -> new ValidationRule("id", null, "field", InputSource.BODY))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("invalidExpression");
        assertThatThrownBy(() -> new ValidationRule("id", "expr", null, InputSource.BODY))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fieldPath");
        assertThatThrownBy(() -> new ValidationRule("id", "expr", "field", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("source");
    }
}
