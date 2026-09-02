package com.consid.automation.camunda;

import com.consid.automation.camunda.internal.Diagnostics;
import com.consid.automation.camunda.internal.feel.FEELRuleGenerator;
import com.consid.automation.camunda.internal.feel.RuleFileWriter;
import com.consid.automation.camunda.internal.model.Endpoint;
import com.consid.automation.camunda.internal.model.ValidationRule;
import com.consid.automation.camunda.internal.openapi.ExtractionResult;
import com.consid.automation.camunda.internal.openapi.FieldTypeResolver;
import com.consid.automation.camunda.internal.openapi.OpenApiOperationScanner;
import com.consid.automation.camunda.internal.openapi.OperationInputs;
import com.consid.automation.camunda.internal.openapi.QueryParameterExtractor;
import com.consid.automation.camunda.internal.openapi.RequiredFieldsExtractor;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Entry point for FEEL validation generation. Coordinates the pipeline:
 * parse OpenAPI → scan operations → extract required body fields and query
 * parameters → build rules → render FEEL → write.
 * Each stage lives in its own collaborator so this class stays a thin orchestrator.
 *
 * <p>Instances are immutable and may be reused; each {@link #generate()} call
 * re-reads the specification. Configure via {@link #builder()}.
 */
public class FEELValidationGenerator {

    private final Path openApiSpecPath;
    private final Path outputFilePath;
    private final FEELRuleGenerator ruleBuilder;
    private final OpenApiOperationScanner scanner;
    private final RuleFileWriter writer;
    private final Diagnostics diagnostics;

    private FEELValidationGenerator(Builder builder) {
        this.openApiSpecPath = builder.openApiSpecPath;
        this.outputFilePath = builder.outputFilePath;
        this.ruleBuilder = new FEELRuleGenerator(
            builder.addResponse, builder.successStatusCode, builder.failureStatusCode);
        this.scanner = new OpenApiOperationScanner(builder.httpMethods, builder.mediaType);
        this.writer = new RuleFileWriter();
        this.diagnostics = new Diagnostics(builder.warningConsumer);
    }

    /**
     * Parses the OpenAPI document, builds the validation rules, and writes the
     * FEEL output file. Parser validation messages (misspelled keywords, missing
     * sections) are reported through the configured warning consumer.
     *
     * @throws FEELGenerationException if the document cannot be parsed, a
     *         {@code $ref} cannot be resolved, or the output cannot be written
     */
    public void generate() {
        OpenAPI openAPI = parseOpenAPI();
        Map<Endpoint, OperationInputs> inputsByEndpoint = scanner.scan(openAPI);
        Map<Endpoint, List<ValidationRule>> rulesByEndpoint = buildRules(openAPI, inputsByEndpoint);
        writeOutput(ruleBuilder.render(rulesByEndpoint));
    }

    private OpenAPI parseOpenAPI() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(openApiSpecPath.toString(), null, options);
        List<String> messages = result == null || result.getMessages() == null ? List.of() : result.getMessages();
        if (result == null || result.getOpenAPI() == null) {
            throw new FEELGenerationException("Failed to parse OpenAPI specification " + openApiSpecPath
                + (messages.isEmpty() ? "" : ": " + String.join("; ", messages)));
        }
        // The parser tolerates structural mistakes (a misspelled `minLength`, a missing `info`
        // block) and still returns a document. Surface them instead of validating a spec the
        // author didn't write.
        String location = String.valueOf(openApiSpecPath.getFileName());
        messages.forEach(message -> diagnostics.warn(location, message));
        return result.getOpenAPI();
    }

    private Map<Endpoint, List<ValidationRule>> buildRules(OpenAPI openAPI,
                                                            Map<Endpoint, OperationInputs> inputsByEndpoint) {
        FieldTypeResolver typeResolver = new FieldTypeResolver(openAPI, diagnostics);
        RequiredFieldsExtractor fieldsExtractor = new RequiredFieldsExtractor(typeResolver, diagnostics);
        QueryParameterExtractor parameterExtractor = new QueryParameterExtractor(typeResolver);
        Map<Endpoint, List<ValidationRule>> rulesByEndpoint = new LinkedHashMap<>();
        inputsByEndpoint.forEach((endpoint, inputs) -> {
            List<ValidationRule> rules = new ArrayList<>();
            if (inputs.hasBody()) {
                rules.addAll(bodyRules(endpoint, inputs.bodySchema(), fieldsExtractor));
            }
            rules.addAll(queryParameterRules(inputs, parameterExtractor));
            if (!rules.isEmpty()) {
                rulesByEndpoint.put(endpoint, rules);
            }
        });
        return rulesByEndpoint;
    }

    private List<ValidationRule> bodyRules(Endpoint endpoint, Schema<?> schema,
                                           RequiredFieldsExtractor fieldsExtractor) {
        ExtractionResult extracted;
        try {
            extracted = fieldsExtractor.extract(schema);
        } catch (IllegalStateException e) {
            // Attach endpoint context so the user can pinpoint a broken $ref in large specs.
            throw new FEELGenerationException("Failed processing " + endpoint + ": " + e.getMessage(), e);
        }
        List<ValidationRule> rules = new ArrayList<>();
        extracted.requiredFields().forEach((fieldPath, descriptor) ->
            rules.add(ruleBuilder.createRule(fieldPath, descriptor)));
        if (extracted.hasRootClosure()) {
            rules.add(ruleBuilder.createRootObjectRule(extracted.rootClosure()));
        }
        return rules;
    }

    private List<ValidationRule> queryParameterRules(OperationInputs inputs,
                                                     QueryParameterExtractor parameterExtractor) {
        List<ValidationRule> rules = new ArrayList<>();
        parameterExtractor.extract(inputs.requiredQueryParameters()).forEach((name, descriptor) ->
            rules.add(ruleBuilder.createQueryParameterRule(name, descriptor)));
        return rules;
    }

    private void writeOutput(String content) {
        try {
            writer.write(outputFilePath, content);
        } catch (IOException e) {
            throw new FEELGenerationException("Failed to write FEEL output to " + outputFilePath, e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Configures a {@link FEELValidationGenerator}. Mirrors the Maven Mojo's
     * parameters one-to-one; {@link #build()} rejects missing paths, an empty
     * method list, and status codes outside 100–599 with
     * {@link NullPointerException} / {@link IllegalArgumentException}.
     */
    public static final class Builder {
        private Path openApiSpecPath;
        private Path outputFilePath;
        private boolean addResponse = false;
        private int successStatusCode = 201;
        private int failureStatusCode = 400;
        private List<String> httpMethods = List.of("POST", "PUT", "PATCH");
        private String mediaType = "application/json";
        private Consumer<String> warningConsumer = message -> {};

        private Builder() {
        }

        public Builder withOpenApiPath(Path openApiSpecPath) {
            this.openApiSpecPath = Objects.requireNonNull(openApiSpecPath, "openApiSpecPath");
            return this;
        }

        public Builder withOutputFilePath(Path outputFilePath) {
            this.outputFilePath = Objects.requireNonNull(outputFilePath, "outputFilePath");
            return this;
        }

        public Builder withResponse(boolean addResponse) {
            this.addResponse = addResponse;
            return this;
        }

        public Builder withSuccessStatusCode(int statusCode) {
            this.successStatusCode = statusCode;
            return this;
        }

        public Builder withFailStatusCode(int statusCode) {
            this.failureStatusCode = statusCode;
            return this;
        }

        public Builder withHttpMethods(List<String> httpMethods) {
            this.httpMethods = List.copyOf(Objects.requireNonNull(httpMethods, "httpMethods"));
            return this;
        }

        /**
         * Request body media type to read schemas from. Matched ignoring parameters
         * and case; a structured-syntax suffix also matches, so
         * {@code application/json} accepts {@code application/vnd.acme+json}.
         */
        public Builder withMediaType(String mediaType) {
            this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
            return this;
        }

        /**
         * Receive each warning the generator emits: OpenAPI parser validation
         * messages and detected-but-unsupported constructs (e.g. an {@code if}/{@code then}
         * predicate shape outside the supported subset, a {@code oneOf} missing its
         * {@code discriminator.mapping}). Messages are pre-formatted with their
         * location. Defaults to a silent no-op; the Maven Mojo wires this to
         * {@code getLog().warn(...)}.
         */
        public Builder withWarningConsumer(Consumer<String> warningConsumer) {
            this.warningConsumer = Objects.requireNonNull(warningConsumer, "warningConsumer");
            return this;
        }

        public FEELValidationGenerator build() {
            Objects.requireNonNull(openApiSpecPath, "openApiSpecPath must be set via withOpenApiPath");
            Objects.requireNonNull(outputFilePath, "outputFilePath must be set via withOutputFilePath");
            if (httpMethods.isEmpty()) {
                throw new IllegalArgumentException("at least one HTTP method must be configured");
            }
            requireValidStatusCode(successStatusCode, "successStatusCode");
            requireValidStatusCode(failureStatusCode, "failStatusCode");
            return new FEELValidationGenerator(this);
        }

        private static void requireValidStatusCode(int statusCode, String name) {
            if (statusCode < 100 || statusCode > 599) {
                throw new IllegalArgumentException(
                    name + " must be a valid HTTP status code (100-599): " + statusCode
                );
            }
        }
    }
}
