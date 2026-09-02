package com.consid.automation.camunda.internal.feel;

import com.consid.automation.camunda.internal.model.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Centralizes all FEEL-specific rule building and rendering logic so that the rest
 * of the generator remains focused on OpenAPI traversal.
 */
public class FEELRuleGenerator implements ValidationRuleBuilder {

    private static final String ACTIVATION_TEMPLATE = """
            {
              req: request.body,
              rules: [
            %s
              ],
              isValid: count(rules[invalid=true])=0
            }.isValid""";

    private static final String RESPONSE_TEMPLATE = """
            {
              req: request.body,
              rules: [
            %s
              ],
              isValid: count(rules[invalid=true])=0,
              body: {
                message: if isValid then "Process successfully started." else "Process creation failed.",
                processInstanceKey: if isValid then correlation.processInstanceKey else null,
                details: rules[invalid=true]
              }, statusCode: if isValid then %d else %d
            }""";

    /** Query parameters are read from the connector's {@code request.params} context. */
    private static final String PARAMS_ROOT = "request.params";
    private static final String PARAMS_FIELD_PREFIX = "params.";
    private static final Pattern SIMPLE_FEEL_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    /**
     * Reserved words the FEEL parser rejects as a dotted path segment
     * ({@code request.params.in} fails to parse). Verified against the Camunda
     * feel-engine; other keywords such as {@code not}, {@code if}, {@code for},
     * {@code date} are accepted after a dot and need no special handling.
     */
    private static final Set<String> FEEL_KEYWORDS = Set.of(
        "and", "or", "in", "then", "else", "function", "true", "false", "null", "satisfies", "return");

    private final boolean addResponse;
    private final FEELExpressionBuilder expressionBuilder;
    private final int successStatusCode;
    private final int failureStatusCode;

    public FEELRuleGenerator(boolean addResponse) {
        this(addResponse, 201, 400);
    }

    public FEELRuleGenerator(boolean addResponse, int successStatusCode, int failureStatusCode) {
        this(addResponse, successStatusCode, failureStatusCode, new FEELExpressionBuilder());
    }

    public FEELRuleGenerator(boolean addResponse,
                      int successStatusCode,
                      int failureStatusCode,
                      FEELExpressionBuilder expressionBuilder) {
        this.addResponse = addResponse;
        this.successStatusCode = successStatusCode;
        this.failureStatusCode = failureStatusCode;
        this.expressionBuilder = expressionBuilder;
    }

    @Override
    public ValidationRule createRule(String fieldPath, FieldDescriptor descriptor) {
        String ruleId = fieldPath + "-invalid";
        String condition = expressionBuilder.build("req." + fieldPath, qualifyDependsOn(descriptor));
        return ValidationRule.create(ruleId, condition, fieldPath);
    }

    @Override
    public ValidationRule createQueryParameterRule(String parameterName, FieldDescriptor descriptor) {
        String condition = expressionBuilder.build(queryParameterAccessor(parameterName), descriptor);
        String fieldPath = PARAMS_FIELD_PREFIX + parameterName;
        return ValidationRule.create(fieldPath + "-invalid", condition, fieldPath);
    }

    /**
     * Query parameter names are not restricted to FEEL identifiers ({@code page-size},
     * {@code filter[status]}) and may collide with FEEL keywords ({@code in}), so
     * anything beyond a plain identifier is read via {@code get value(request.params, "<name>")}
     * instead of a dotted path the engine would misparse.
     */
    private static String queryParameterAccessor(String parameterName) {
        if (SIMPLE_FEEL_NAME.matcher(parameterName).matches() && !FEEL_KEYWORDS.contains(parameterName)) {
            return PARAMS_ROOT + "." + parameterName;
        }
        return "get value(" + PARAMS_ROOT + ", \"" + FEELExpressionBuilder.escapeLiteral(parameterName) + "\")";
    }

    @Override
    public ValidationRule createRootObjectRule(ObjectTypeInfo rootClosure) {
        FieldDescriptor descriptor = FieldDescriptor.of(rootClosure);
        String condition = expressionBuilder.build("req", descriptor);
        return ValidationRule.create("rootObject-invalid", condition, "(root)");
    }

    private FieldDescriptor qualifyDependsOn(FieldDescriptor descriptor) {
        if (!descriptor.isConditional()) {
            return descriptor;
        }
        List<Trigger> qualified = descriptor.dependsOn().stream()
            .map(t -> t.withPrefix("req."))
            .toList();
        return descriptor.withDependsOn(qualified);
    }

    @Override
    public String render(Map<String, List<ValidationRule>> rulesByEndpoint) {
        return rulesByEndpoint.entrySet().stream()
            .map(entry -> entry.getKey() + "\n" + buildRulesBlock(entry.getValue()))
            .collect(Collectors.joining("\n\n"));
    }

    private String buildRulesBlock(List<ValidationRule> rules) {
        String renderedRules = rules.stream()
            .map(rule -> "    " + formatRuleLine(rule))
            .collect(Collectors.joining(",\n"));
        return addResponse
            ? RESPONSE_TEMPLATE.formatted(renderedRules, successStatusCode, failureStatusCode)
            : ACTIVATION_TEMPLATE.formatted(renderedRules);
    }

    private String formatRuleLine(ValidationRule rule) {
        if (addResponse) {
            return "{ id: \"" + rule.id()
                + "\", field: \"" + rule.fieldPath()
                + "\", invalid: " + rule.invalidExpression() + " }";
        }
        return "{invalid: " + rule.invalidExpression() + "}";
    }
}
