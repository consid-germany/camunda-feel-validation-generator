package com.consid.automation.camunda;

import com.consid.automation.camunda.internal.Diagnostics;
import com.consid.automation.camunda.internal.feel.*;
import com.consid.automation.camunda.internal.model.*;
import com.consid.automation.camunda.internal.openapi.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;

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
 * parse OpenAPI → scan operations → extract required fields → render FEEL → write.
 * Each stage lives in its own collaborator so this class stays a thin orchestrator.
 */
public class FEELValidationGenerator {

    private final Path openApiSpecPath;
    private final Path outputFilePath;
    private final ValidationRuleBuilder ruleBuilder;
    private final OpenApiOperationScanner scanner;
    private final RuleFileWriter writer;
    private final Diagnostics diagnostics;

    private FEELValidationGenerator(Builder builder) {
        this.openApiSpecPath = builder.openApiSpecPath;
        this.outputFilePath = builder.outputFilePath;
        this.ruleBuilder = builder.customRuleBuilder != null
            ? builder.customRuleBuilder
            : new FEELRuleGenerator(builder.addResponse, builder.successStatusCode, builder.failureStatusCode);
        this.scanner = new OpenApiOperationScanner(builder.httpMethods, builder.mediaType);
        this.writer = new RuleFileWriter();
        this.diagnostics = new Diagnostics(builder.warningConsumer);
    }

    public void generate() throws IOException {
        OpenAPI openAPI = parseOpenAPI();
        Map<String, Schema<?>> schemasByEndpoint = scanner.scan(openAPI);
        Map<String, List<ValidationRule>> rulesByEndpoint = buildRules(openAPI, schemasByEndpoint);
        writer.write(outputFilePath, ruleBuilder.render(rulesByEndpoint));
    }

    private OpenAPI parseOpenAPI() throws IOException {
        OpenAPI openAPI = new OpenAPIV3Parser().read(openApiSpecPath.toString());
        if (openAPI == null) {
            throw new IOException("Failed to parse OpenAPI specification: " + openApiSpecPath);
        }
        return openAPI;
    }

    private Map<String, List<ValidationRule>> buildRules(OpenAPI openAPI,
                                                          Map<String, Schema<?>> schemasByEndpoint) {
        RequiredFieldsExtractor fieldsExtractor =
            new RequiredFieldsExtractor(new FieldTypeResolver(openAPI, diagnostics), diagnostics);
        Map<String, List<ValidationRule>> rulesByEndpoint = new LinkedHashMap<>();
        schemasByEndpoint.forEach((heading, schema) -> {
            List<ValidationRule> rules = rulesFor(heading, schema, fieldsExtractor);
            if (!rules.isEmpty()) {
                rulesByEndpoint.put(heading, rules);
            }
        });
        return rulesByEndpoint;
    }

    private List<ValidationRule> rulesFor(String heading, Schema<?> schema,
                                          RequiredFieldsExtractor fieldsExtractor) {
        ExtractionResult extracted;
        try {
            extracted = fieldsExtractor.extract(schema);
        } catch (IllegalStateException e) {
            // Attach endpoint context so the user can pinpoint a broken $ref in large specs.
            throw new IllegalStateException(
                "Failed processing " + heading + ": " + e.getMessage(), e);
        }
        List<ValidationRule> rules = new ArrayList<>();
        extracted.requiredFields().forEach((fieldPath, descriptor) ->
            rules.add(ruleBuilder.createRule(fieldPath, descriptor)));
        if (extracted.hasRootClosure()) {
            rules.add(ruleBuilder.createRootObjectRule(extracted.rootClosure()));
        }
        return rules;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path openApiSpecPath;
        private Path outputFilePath;
        private boolean addResponse = false;
        private int successStatusCode = 201;
        private int failureStatusCode = 400;
        private List<String> httpMethods = List.of("POST", "PUT", "PATCH");
        private String mediaType = "application/json";
        private ValidationRuleBuilder customRuleBuilder;
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

        public Builder withMediaType(String mediaType) {
            this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
            return this;
        }

        Builder withRuleBuilder(ValidationRuleBuilder ruleBuilder) {
            this.customRuleBuilder = ruleBuilder;
            return this;
        }

        /**
         * Receive each detected-but-unsupported construct the generator skipped
         * (e.g. an {@code if}/{@code then} predicate shape outside the supported
         * subset, an {@code oneOf} missing its {@code discriminator.mapping}).
         * Messages are pre-formatted with the schema location. Defaults to a
         * silent no-op; the Maven Mojo wires this to {@code getLog().warn(...)}.
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
