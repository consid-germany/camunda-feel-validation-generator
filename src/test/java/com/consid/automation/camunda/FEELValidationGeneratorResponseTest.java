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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering response-expression generation. Two layers:
 * <ul>
 *   <li>A parameterized run over the shared {@link
 *   AbstractFEELValidationGeneratorIntegrationTest#scenarios() scenario table}
 *   that verifies every generated expression is parseable+evaluable by the FEEL
 *   engine and carries the expected {@code isValid} / {@code statusCode}. The
 *   per-rule {@code invalidExpression} is byte-identical to activation mode
 *   ({@link FEELRuleGenerator#createRule} ignores {@code addResponse}), so
 *   deep per-payload boolean semantics are already covered by the activation
 *   test and need not be re-asserted here.</li>
 *   <li>A single snapshot test that pins the exact response body shape
 *   ({@code message}, {@code processInstanceKey}, {@code details}) against a
 *   known-good fixture — the only guard against silent renames in
 *   {@code RESPONSE_TEMPLATE}.</li>
 * </ul>
 */
public class FEELValidationGeneratorResponseTest extends AbstractFEELValidationGeneratorIntegrationTest {

    private static final Comparator<Number> NUMBER_COMPARATOR =
        (a, b) -> Double.compare(a.doubleValue(), b.doubleValue());

    @ParameterizedTest
    @MethodSource("com.consid.automation.camunda.AbstractFEELValidationGeneratorIntegrationTest#scenarios")
    public void test_response_does_emit_parseable_feel_with_expected_verdict_as_expected(Scenario scenario) throws IOException {
        // given
        Path specFile = resolveResourcePath(scenario.openApiResource());
        Path outputFile = tempDir.resolve(scenario.id() + "-response.feel");
        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath())
            .withOutputFilePath(outputFile.toAbsolutePath())
            .withResponse(true)
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
                .as("Response FEEL should evaluate without engine error for %s", scenario.id())
                .withFailMessage(() -> "FEEL evaluation failure: " + evaluation.left().get())
                .isTrue();

            Map<String, Object> feelContext = toJavaMap(evaluation.getOrElse(null));
            assertThat(feelContext)
                .as("Response context shape for %s", scenario.id())
                .containsKeys("isValid", "statusCode", "body");
            assertThat((Boolean) feelContext.get("isValid"))
                .as("Response isValid for %s", scenario.id())
                .isEqualTo(scenario.expectedValid());
            assertThat(intStatus(feelContext.get("statusCode")))
                .as("Response statusCode for %s", scenario.id())
                .isEqualTo(scenario.expectedValid() ? 201 : 400);
        }
    }

    @Test
    public void test_response_body_does_match_snapshot_for_valid_payload_as_expected() throws IOException {
        runBodySnapshotScenario(
            "responses-direct-valid",
            "openapi/responses-direct-api.json",
            "payloads/responses-direct-variables.json",
            "response/responses-direct-valid-body.json",
            true
        );
    }

    @Test
    public void test_response_body_does_match_snapshot_for_invalid_payload_as_expected() throws IOException {
        runBodySnapshotScenario(
            "responses-direct-invalid",
            "openapi/responses-direct-api.json",
            "payloads/responses-direct-invalid-variables.json",
            "response/responses-direct-invalid-body.json",
            false
        );
    }

    /**
     * Pins the exact {@code body} shape of the response context against a JSON
     * snapshot — guards the response template's field names and layout.
     */
    private void runBodySnapshotScenario(String scenarioId,
                                         String openApiResource,
                                         String payloadResource,
                                         String expectedBodyResource,
                                         boolean expectedValid) throws IOException {
        // given
        Path specFile = resolveResourcePath(openApiResource);
        Path outputFile = tempDir.resolve(scenarioId + ".feel");
        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath())
            .withOutputFilePath(outputFile.toAbsolutePath())
            .withResponse(true)
            .build();
        Map<String, Object> context = buildEvaluationContext(loadJsonResource(payloadResource));
        Map<String, Object> expectedBody = loadJsonResource(expectedBodyResource);

        // when
        generator.generate();
        String actualOutput = Files.readString(outputFile).stripTrailing();
        List<String> expressions = extractFeelExpressions(actualOutput);

        // then
        assertThat(expressions)
            .as("FEEL expressions should exist for %s", scenarioId)
            .isNotEmpty();
        for (String expression : expressions) {
            var evaluation = FEEL_ENGINE.evalExpression(expression, context);
            assertThat(evaluation.isRight())
                .as("FEEL expression should evaluate for %s", scenarioId)
                .withFailMessage(() -> "FEEL evaluation failure: " + evaluation.left().get())
                .isTrue();

            Map<String, Object> feelContext = toJavaMap(evaluation.getOrElse(null));
            assertThat((Boolean) feelContext.get("isValid"))
                .as("Response validity for %s", scenarioId)
                .isEqualTo(expectedValid);
            assertThat(intStatus(feelContext.get("statusCode")))
                .as("Status code for %s", scenarioId)
                .isEqualTo(expectedValid ? 201 : 400);
            assertThat(castToMap(normalizeValue(feelContext.get("body"))))
                .as("Response body for %s", scenarioId)
                .usingRecursiveComparison()
                .withComparatorForType(NUMBER_COMPARATOR, Number.class)
                .isEqualTo(castToMap(normalizeValue(expectedBody)));
        }
    }

    private static int intStatus(Object status) {
        return status instanceof Number number ? number.intValue() : Integer.parseInt(status.toString());
    }
}
