package com.consid.automation.camunda.internal.feel;

import com.consid.automation.camunda.internal.model.Endpoint;
import com.consid.automation.camunda.internal.model.FeelString;
import com.consid.automation.camunda.internal.model.FieldDescriptor;
import com.consid.automation.camunda.internal.model.ObjectTypeInfo;
import com.consid.automation.camunda.internal.model.Trigger;
import com.consid.automation.camunda.internal.model.ValidationRule;
import com.consid.automation.camunda.internal.model.ValidationRule.InputSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns field descriptors into {@link ValidationRule}s and renders the grouped
 * rules as the final FEEL text, either an activation condition (boolean) or a
 * response expression (context with body and status code). Owns every piece of
 * FEEL syntax outside the per-field violation expressions, which live in
 * {@link FEELExpressionBuilder}.
 */
public final class FEELRuleGenerator {

    /** Bound only when at least one rule reads the body; query-only endpoints leave it out. */
    private static final String BODY_ALIAS = "  req: request.body,\n";

    private static final String ACTIVATION_TEMPLATE = """
            {
            %s  rules: [
            %s
              ],
              isValid: count(rules[invalid=true])=0
            }.isValid""";

    private static final String RESPONSE_TEMPLATE = """
            {
            %s  rules: [
            %s
              ],
              isValid: count(rules[invalid=true])=0,
              body: {
                message: if isValid then "Process successfully started." else "Process creation failed.",
                processInstanceKey: if isValid then correlation.processInstanceKey else null,
                details: rules[invalid=true]
              }, statusCode: if isValid then %d else %d
            }""";

    /** Body fields are read through the alias the template binds to {@code request.body}. */
    private static final String BODY_ROOT = "req";
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
    private final int successStatusCode;
    private final int failureStatusCode;
    private final FEELExpressionBuilder expressionBuilder = new FEELExpressionBuilder();

    public FEELRuleGenerator(boolean addResponse, int successStatusCode, int failureStatusCode) {
        this.addResponse = addResponse;
        this.successStatusCode = successStatusCode;
        this.failureStatusCode = failureStatusCode;
    }

    /** Rule for a required request-body field addressed by its dotted path. */
    public ValidationRule createRule(String fieldPath, FieldDescriptor descriptor) {
        String condition = expressionBuilder.build(BODY_ROOT + "." + fieldPath, qualifyDependsOn(descriptor));
        return new ValidationRule(fieldPath + "-invalid", condition, fieldPath, InputSource.BODY);
    }

    /**
     * Rule enforcing the root payload's {@code additionalProperties: false}
     * closure. Separate from the per-field rules because the root has no parent
     * property to attach to.
     */
    public ValidationRule createRootObjectRule(ObjectTypeInfo rootClosure) {
        String condition = expressionBuilder.build(BODY_ROOT, FieldDescriptor.of(rootClosure));
        return new ValidationRule("rootObject-invalid", condition, "(root)", InputSource.BODY);
    }

    /**
     * Rule for a query parameter marked {@code required: true}. Query parameters
     * live under {@code request.params}, not the request body, and are
     * identified as {@code params.<name>} so they cannot collide with a body
     * field of the same name.
     */
    public ValidationRule createQueryParameterRule(String parameterName, FieldDescriptor descriptor) {
        String condition = expressionBuilder.build(queryParameterAccessor(parameterName), descriptor);
        String fieldPath = PARAMS_FIELD_PREFIX + parameterName;
        return new ValidationRule(fieldPath + "-invalid", condition, fieldPath, InputSource.QUERY);
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
        return "get value(" + PARAMS_ROOT + ", " + new FeelString(parameterName).render() + ")";
    }

    private FieldDescriptor qualifyDependsOn(FieldDescriptor descriptor) {
        if (!descriptor.isConditional()) {
            return descriptor;
        }
        List<Trigger> qualified = descriptor.dependsOn().stream()
            .map(trigger -> trigger.withPrefix(BODY_ROOT + "."))
            .toList();
        return descriptor.withDependsOn(qualified);
    }

    /** One FEEL block per endpoint, each introduced by a {@code # METHOD /path} heading. */
    public String render(Map<Endpoint, List<ValidationRule>> rulesByEndpoint) {
        return rulesByEndpoint.entrySet().stream()
            .map(entry -> heading(entry.getKey()) + "\n" + buildRulesBlock(entry.getValue()))
            .collect(Collectors.joining("\n\n"));
    }

    private static String heading(Endpoint endpoint) {
        return "# " + endpoint.method() + " " + endpoint.path();
    }

    private String buildRulesBlock(List<ValidationRule> rules) {
        String renderedRules = rules.stream()
            .map(rule -> "    " + formatRuleLine(rule))
            .collect(Collectors.joining(",\n"));
        boolean readsBody = rules.stream().anyMatch(rule -> rule.source() == InputSource.BODY);
        String bodyAlias = readsBody ? BODY_ALIAS : "";
        return addResponse
            ? RESPONSE_TEMPLATE.formatted(bodyAlias, renderedRules, successStatusCode, failureStatusCode)
            : ACTIVATION_TEMPLATE.formatted(bodyAlias, renderedRules);
    }

    private String formatRuleLine(ValidationRule rule) {
        if (addResponse) {
            return "{ id: " + new FeelString(rule.id()).render()
                + ", field: " + new FeelString(rule.fieldPath()).render()
                + ", invalid: " + rule.invalidExpression() + " }";
        }
        return "{invalid: " + rule.invalidExpression() + "}";
    }
}
