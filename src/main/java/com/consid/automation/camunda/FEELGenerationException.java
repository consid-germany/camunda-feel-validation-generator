package com.consid.automation.camunda;

/**
 * Signals that FEEL generation failed: the OpenAPI document could not be parsed,
 * a {@code $ref} could not be resolved, or the output file could not be written.
 * The message names the endpoint or location where possible; the cause, when
 * present, is the underlying parser or I/O exception.
 */
public class FEELGenerationException extends RuntimeException {

    public FEELGenerationException(String message) {
        super(message);
    }

    public FEELGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
