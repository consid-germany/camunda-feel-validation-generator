package com.consid.automation.camunda;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests Mojo-specific behavior: parameter wiring at the {@code @Parameter}
 * boundary, log output, and the mapping of failures onto Maven's exception
 * types. Pipeline correctness is covered by the integration and unit tests for
 * the underlying classes.
 */
public class FEELValidationGeneratorMojoTest {

    private static final String DEFAULT_METHODS = "POST,PUT,PATCH";

    @TempDir
    Path tempDir;

    private FEELValidationGeneratorMojo mojo;
    private RecordingLog log;

    @BeforeEach
    void setUp() {
        mojo = new FEELValidationGeneratorMojo();
        log = new RecordingLog();
        mojo.setLog(log);
    }

    @Test
    public void test_mojo_does_run_pipeline_and_log_completion() throws Exception {
        // given
        Path outputFile = tempDir.resolve("output.feel");
        configure(resourcePath("openapi/responses-direct-api.json"), outputFile, DEFAULT_METHODS);

        // when
        mojo.execute();

        // then
        assertThat(outputFile).as("Mojo should produce the configured output file").exists();
        assertThat(log.infos).contains(
            "Starting FEEL Validation Generator",
            "FEEL validation generation completed successfully");
    }

    @Test
    public void test_mojo_does_forward_generator_warnings_to_maven_log() throws Exception {
        // given — a spec with a misspelled keyword the parser reports
        configure(resourcePath("openapi/typo-api.json"), tempDir.resolve("output.feel"), DEFAULT_METHODS);

        // when
        mojo.execute();

        // then
        assertThat(log.warnings).anySatisfy(warning -> assertThat(warning).contains("minLenght"));
    }

    @Test
    public void test_mojo_does_fail_when_openapi_spec_missing() {
        // given
        configure(Path.of("/nonexistent/api-spec.yaml"), tempDir.resolve("output.feel"), DEFAULT_METHODS);

        // when // then
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("OpenAPI specification file not found");
    }

    @Test
    public void test_mojo_does_report_spec_problems_as_build_failure() {
        // given — a $ref pointing at a missing component
        configure(resourcePath("openapi/broken-ref-api.json"), tempDir.resolve("output.feel"), DEFAULT_METHODS);

        // when // then
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("POST /customers/broken")
            .hasMessageContaining("#/components/schemas/DoesNotExist");
    }

    @Test
    public void test_mojo_does_report_unwritable_output_as_build_failure() {
        // given — the output path is an existing directory
        configure(resourcePath("openapi/responses-direct-api.json"), tempDir, DEFAULT_METHODS);

        // when // then
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("Failed to write FEEL output");
    }

    @Test
    public void test_mojo_does_wrap_unexpected_errors_in_execution_exception() {
        // given — an empty method list is a programming/config error the Builder rejects
        configure(resourcePath("openapi/responses-direct-api.json"), tempDir.resolve("output.feel"), ",");

        // when // then
        assertThatThrownBy(mojo::execute)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Error generating FEEL validations")
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    /** Maven injects {@code @Parameter} fields directly; the test mirrors that rather than adding setters. */
    private void configure(Path openApiSpec, Path outputFile, String methods) {
        setField("openApiSpec", openApiSpec.toFile());
        setField("outputFile", outputFile.toFile());
        setField("addResponse", false);
        setField("methods", methods);
        setField("successStatusCode", 201);
        setField("failStatusCode", 400);
        setField("mediaType", "application/json");
    }

    private void setField(String name, Object value) {
        try {
            var field = FEELValidationGeneratorMojo.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(mojo, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Mojo parameter field '" + name + "' not found; was it renamed?", e);
        }
    }

    private Path resourcePath(String resourceName) {
        URL url = getClass().getClassLoader().getResource(resourceName);
        assertThat(url).as(resourceName + " should exist in test resources").isNotNull();
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
