package com.consid.automation.camunda.internal;

import java.util.function.Consumer;

/**
 * Routes build-time warnings to the consumer configured on the generator. Two
 * kinds of message flow through here: OpenAPI parser validation messages, and
 * constructs the generator detected but does not model — an {@code if}/{@code then}
 * predicate outside the supported subset, a {@code oneOf} without
 * {@code discriminator.mapping}, schema-form {@code additionalProperties}, a
 * {@code required} name no schema declares. Reporting instead of silently
 * skipping keeps the emitted FEEL honest about what it enforces.
 *
 * <p>Messages are pre-formatted as {@code [<location>] <message>}. The Maven Mojo
 * wires the consumer to {@code getLog().warn(...)}; the programmatic API defaults
 * to a no-op unless {@code Builder.withWarningConsumer} is used.
 */
public final class Diagnostics {

    /** Discards every warning. For callers and tests that don't observe diagnostics. */
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
