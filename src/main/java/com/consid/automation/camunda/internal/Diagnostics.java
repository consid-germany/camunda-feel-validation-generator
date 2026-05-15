package com.consid.automation.camunda.internal;

import java.util.function.Consumer;

/**
 * Collects warnings about author-detected-but-unsupported constructs in the
 * input spec. Lets the generator stay loud about "I saw this, I ignored it"
 * cases that used to be silent footguns:
 *
 * <ul>
 *   <li>An {@code if}/{@code then} predicate shape the extractor doesn't model</li>
 *   <li>{@code oneOf} without a {@code discriminator} + mapping (falling back to union-merge)</li>
 *   <li>Schema-form {@code additionalProperties} (only the boolean-false case is honored)</li>
 * </ul>
 *
 * <p>The consumer receives an already-formatted message string. The Mojo wires
 * it to {@code getLog().warn(...)}; the programmatic API defaults to a no-op
 * (the caller can pass their own consumer via {@code Builder.withWarningConsumer}).
 */
public final class Diagnostics {

    public static final Diagnostics NOOP = new Diagnostics(message -> {});

    private final Consumer<String> consumer;

    public Diagnostics(Consumer<String> consumer) {
        this.consumer = consumer;
    }

    /**
     * Emit a warning. {@code location} should identify where in the schema the
     * problem was found (e.g. a dotted field path or {@code "(root)"});
     * {@code message} explains what was skipped and how to fix it.
     */
    public void warn(String location, String message) {
        consumer.accept("[" + location + "] " + message);
    }
}
