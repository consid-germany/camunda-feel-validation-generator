package com.consid.automation.camunda;

import com.consid.automation.camunda.internal.feel.*;
import com.consid.automation.camunda.internal.model.*;
import com.consid.automation.camunda.internal.openapi.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests covering activation-condition generation.
 * Drives the shared {@link AbstractFEELValidationGeneratorIntegrationTest#scenarios()
 * scenario table} — each row generates FEEL, evaluates every emitted expression
 * against the Camunda FEEL engine, and asserts the engine's verdict matches the
 * scenario's expected boolean. The assertion is the engine's result, not the
 * textual output.
 */
public class FEELValidationGeneratorActivationTest extends AbstractFEELValidationGeneratorIntegrationTest {

    @Test
    public void test_unresolved_ref_does_include_endpoint_context_in_error_as_expected() {
        // given — a spec with a $ref pointing at a missing component
        Path specFile = resolveResourcePath("openapi/broken-ref-api.json");
        Path outputFile = tempDir.resolve("broken.feel");
        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath())
            .withOutputFilePath(outputFile.toAbsolutePath())
            .withResponse(false)
            .build();

        // when / then — the error should carry both the bad $ref AND the endpoint heading
        // so the user can locate the problem in a multi-endpoint spec.
        assertThatThrownBy(generator::generate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("POST /customers/broken")
            .hasMessageContaining("#/components/schemas/DoesNotExist");
    }

    @ParameterizedTest
    @MethodSource("com.consid.automation.camunda.AbstractFEELValidationGeneratorIntegrationTest#scenarios")
    public void test_activation_does_evaluate_to_expected_verdict_as_expected(Scenario scenario) throws IOException {
        // given
        Path specFile = resolveResourcePath(scenario.openApiResource());
        Path outputFile = tempDir.resolve(scenario.id() + ".feel");
        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath())
            .withOutputFilePath(outputFile.toAbsolutePath())
            .withResponse(false)
            .build();
        Map<String, Object> context = buildEvaluationContext(loadJsonResource(scenario.payloadResource()));

        // when
        generator.generate();
        String actualOutput = Files.readString(outputFile).stripTrailing();
        List<String> expressions = extractFeelExpressions(actualOutput);

        // then
        assertThat(expressions)
            .as("FEEL expressions should exist for %s", scenario.id())
            .isNotEmpty();
        for (String expression : expressions) {
            var evaluation = FEEL_ENGINE.evalExpression(expression, context);
            assertThat(evaluation.isRight())
                .as("FEEL expression should evaluate for %s", scenario.id())
                .withFailMessage(() -> "FEEL evaluation failure: " + evaluation.left().get())
                .isTrue();
            assertThat((Boolean) evaluation.getOrElse(null))
                .as("Activation result for %s", scenario.id())
                .isEqualTo(scenario.expectedValid());
        }
    }
}
