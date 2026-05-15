package com.consid.automation.camunda;

import com.consid.automation.camunda.internal.feel.*;
import com.consid.automation.camunda.internal.model.*;
import com.consid.automation.camunda.internal.openapi.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests Mojo-specific behavior: parameter wiring at the @Parameter boundary
 * and the two Mojo-only error paths (missing input file, unexpected I/O error).
 * Pipeline correctness is covered by the integration and unit tests for the
 * underlying classes.
 */
public class FEELValidationGeneratorMojoTest {

    @TempDir
    Path tempDir;

    private FEELValidationGeneratorMojo mojo;
    private Log mockLog;

    @BeforeEach
    void setUp() {
        mojo = new FEELValidationGeneratorMojo();
        mockLog = mock(Log.class);
        mojo.setLog(mockLog);
    }

    @Test
    public void test_mojo_does_run_pipeline_and_log_completion_as_expected() throws Exception {
        // given
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output.feel");
        setMojoFields(specFile.toAbsolutePath().toString(), outputFile.toAbsolutePath().toString());

        // when
        mojo.execute();

        // then
        assertThat(Files.exists(outputFile))
            .as("Mojo should produce the configured output file")
            .isTrue();
        verify(mockLog).info("Starting FEEL Validation Generator");
        verify(mockLog).info("FEEL validation generation completed successfully");
    }

    @Test
    public void test_mojo_does_throw_failure_when_openapi_spec_missing_as_expected() {
        // given
        setMojoFields("/nonexistent/api-spec.yaml", tempDir.resolve("output.feel").toAbsolutePath().toString());

        // when // then
        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("OpenAPI specification file not found");
    }

    @Test
    public void test_mojo_does_wrap_unexpected_errors_in_execution_exception_as_expected() throws Exception {
        // given
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        setMojoFields(specFile.toAbsolutePath().toString(), tempDir.toAbsolutePath().toString());

        // when // then
        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Error generating FEEL validations");
    }

    private void setMojoFields(String openApiSpec, String outputFile) {
        try {
            setField("openApiSpec", openApiSpec);
            setField("outputFile", outputFile);
            setField("addResponse", false);
            setField("methods", "POST,PUT,PATCH");
            setField("successStatusCode", 201);
            setField("failStatusCode", 400);
            setField("mediaType", "application/json");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set Mojo fields", e);
        }
    }

    private void setField(String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        var field = FEELValidationGeneratorMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    private Path copyResourceToTempDir(String resourceName) throws IOException {
        URL resourceUrl = getClass().getClassLoader().getResource(resourceName);
        assertThat(resourceUrl)
            .as(resourceName + " should exist in test resources")
            .isNotNull();

        Path destination = tempDir.resolve(resourceName);
        Path parentDir = destination.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
        try (InputStream inputStream = resourceUrl.openStream()) {
            Files.write(destination, inputStream.readAllBytes());
        }
        return destination;
    }
}
