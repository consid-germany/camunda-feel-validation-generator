package com.consid.automation.camunda.internal.openapi;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.List;

/**
 * The validatable inputs of one OpenAPI operation, as yielded by
 * {@link OpenApiOperationScanner}:
 * <ul>
 *   <li>{@code bodySchema} — the request body schema for the configured media
 *       type, or {@code null} when the operation declares none.</li>
 *   <li>{@code requiredQueryParameters} — every {@code in: query} parameter
 *       marked {@code required: true}, path-level entries first, then the
 *       operation's own (which override same-named path-level ones).</li>
 * </ul>
 * An operation contributing neither is not scanned at all.
 */
public record OperationInputs(Schema<?> bodySchema, List<Parameter> requiredQueryParameters) {

    public OperationInputs {
        requiredQueryParameters = requiredQueryParameters == null
            ? List.of()
            : List.copyOf(requiredQueryParameters);
    }

    public boolean hasBody() {
        return bodySchema != null;
    }

    public boolean hasRequiredQueryParameters() {
        return !requiredQueryParameters.isEmpty();
    }

    public boolean isEmpty() {
        return !hasBody() && !hasRequiredQueryParameters();
    }
}
