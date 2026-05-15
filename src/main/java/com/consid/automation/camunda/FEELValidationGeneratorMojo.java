package com.consid.automation.camunda;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for generating FEEL validation rules from OpenAPI specifications.
 * 
 * This plugin reads an OpenAPI specification file and generates a validation
 * output text file containing FEEL validation rules.
 */
@Mojo(name = "generate-feel", defaultPhase = org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES)
public class FEELValidationGeneratorMojo extends AbstractMojo {

    /**
     * The path to the OpenAPI specification file to be processed.
     */
    @Parameter(property = "feelValidationGenerator.openApiSpec", required = true)
    private String openApiSpec;

    /**
     * The path where the generated validation output file will be written.
     */
    @Parameter(property = "feelValidationGenerator.outputFile", required = true)
    private String outputFile;

    /**
     * Flag to include a response body/status block in the generated FEEL output.
     */
    @Parameter(property = "feelValidationGenerator.addResponse", defaultValue = "false")
    private boolean addResponse;

    /**
     * HTTP status code to use when the response expression evaluates to success.
     */
    @Parameter(property = "feelValidationGenerator.successStatusCode", defaultValue = "201")
    private int successStatusCode;

    /**
     * HTTP status code to use when the response expression evaluates to failure.
     */
    @Parameter(property = "feelValidationGenerator.failStatusCode", defaultValue = "400")
    private int failStatusCode;

    /**
     * Comma-separated HTTP methods (e.g. POST,PUT,PATCH) to scan for request bodies.
     */
    @Parameter(property = "feelValidationGenerator.methods", defaultValue = "POST,PUT,PATCH")
    private String methods;

    /**
     * Request body media type to read schemas from. Operations declaring multiple
     * content types only contribute the schema matching this media type.
     */
    @Parameter(property = "feelValidationGenerator.mediaType", defaultValue = "application/json")
    private String mediaType;

    /**
     * Executes the FEEL validation generation logic.
     *
     * @throws MojoExecutionException if an unexpected error occurs
     * @throws MojoFailureException if the plugin execution fails
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            getLog().info("Starting FEEL Validation Generator");
            getLog().info("Input OpenAPI spec: " + openApiSpec);
            getLog().info("Output file: " + outputFile);

            Path specPath = Path.of(openApiSpec);
            if (!Files.exists(specPath)) {
                throw new MojoFailureException("OpenAPI specification file not found: " + openApiSpec);
            }

            List<String> methodList = Arrays.stream(methods.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .toList();

            FEELValidationGenerator generator = FEELValidationGenerator.builder()
                .withOpenApiPath(specPath)
                .withOutputFilePath(Path.of(outputFile))
                .withResponse(addResponse)
                .withSuccessStatusCode(successStatusCode)
                .withFailStatusCode(failStatusCode)
                .withHttpMethods(methodList)
                .withMediaType(mediaType)
                .withWarningConsumer(message -> getLog().warn(message))
                .build();
            generator.generate();

            getLog().info("FEEL validation generation completed successfully");
            getLog().info("Output written to: " + outputFile);
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Error generating FEEL validations", e);
        }
    }
}
