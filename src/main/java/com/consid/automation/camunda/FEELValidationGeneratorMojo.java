package com.consid.automation.camunda;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Generates FEEL validation rules from an OpenAPI specification. Thin adapter
 * over {@link FEELValidationGenerator}: validates the input file exists, maps
 * the {@code @Parameter} set onto the Builder, and translates failures into
 * Maven's exception vocabulary.
 */
@Mojo(name = "generate-feel", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class FEELValidationGeneratorMojo extends AbstractMojo {

    /**
     * The OpenAPI specification file to process. Relative paths resolve against
     * the project base directory.
     */
    @Parameter(property = "feelValidationGenerator.openApiSpec", required = true)
    private File openApiSpec;

    /**
     * Where the generated FEEL output is written. Parent directories are created.
     * Relative paths resolve against the project base directory.
     */
    @Parameter(property = "feelValidationGenerator.outputFile", required = true)
    private File outputFile;

    /**
     * {@code true} emits a response expression (context with body and status
     * code), {@code false} an activation condition (boolean).
     */
    @Parameter(property = "feelValidationGenerator.addResponse", defaultValue = "false")
    private boolean addResponse;

    /**
     * HTTP status code returned in response mode when validation passes.
     */
    @Parameter(property = "feelValidationGenerator.successStatusCode", defaultValue = "201")
    private int successStatusCode;

    /**
     * HTTP status code returned in response mode when validation fails.
     */
    @Parameter(property = "feelValidationGenerator.failStatusCode", defaultValue = "400")
    private int failStatusCode;

    /**
     * Comma-separated HTTP methods (e.g. {@code POST,PUT,PATCH}) whose operations
     * are scanned for request bodies and required query parameters.
     */
    @Parameter(property = "feelValidationGenerator.methods", defaultValue = "POST,PUT,PATCH")
    private String methods;

    /**
     * Request body media type to read schemas from. Matched ignoring parameters
     * and case; {@code application/json} also accepts {@code application/*+json}.
     */
    @Parameter(property = "feelValidationGenerator.mediaType", defaultValue = "application/json")
    private String mediaType;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Starting FEEL Validation Generator");
        getLog().info("Input OpenAPI spec: " + openApiSpec);
        getLog().info("Output file: " + outputFile);

        Path specPath = openApiSpec.toPath();
        if (!Files.exists(specPath)) {
            throw new MojoFailureException("OpenAPI specification file not found: " + openApiSpec);
        }

        try {
            FEELValidationGenerator.builder()
                .withOpenApiPath(specPath)
                .withOutputFilePath(outputFile.toPath())
                .withResponse(addResponse)
                .withSuccessStatusCode(successStatusCode)
                .withFailStatusCode(failStatusCode)
                .withHttpMethods(parseMethods(methods))
                .withMediaType(mediaType)
                .withWarningConsumer(message -> getLog().warn(message))
                .build()
                .generate();
        } catch (FEELGenerationException e) {
            // Spec-level problems are the author's to fix: a build failure, not a plugin crash.
            throw new MojoFailureException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Error generating FEEL validations", e);
        }

        getLog().info("FEEL validation generation completed successfully");
        getLog().info("Output written to: " + outputFile);
    }

    private static List<String> parseMethods(String methods) {
        return Arrays.stream(methods.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toUpperCase)
            .toList();
    }
}
