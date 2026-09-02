package com.consid.automation.camunda.internal.openapi;

import java.util.Locale;
import java.util.Objects;

/**
 * Decides whether a request body's declared media type satisfies the configured
 * one. Parameters ({@code ; charset=utf-8}) and case are ignored, and a
 * structured-syntax suffix is honored: {@code application/vnd.acme+json}
 * satisfies a configured {@code application/json}. {@link #isExact} lets the
 * scanner prefer an exact match when an operation declares both forms.
 */
final class MediaTypeMatcher {

    private final String configuredEssence;
    private final String configuredType;
    private final String configuredSubtype;

    MediaTypeMatcher(String configured) {
        this.configuredEssence = essence(Objects.requireNonNull(configured, "mediaType"));
        int slash = configuredEssence.indexOf('/');
        this.configuredType = slash < 0 ? configuredEssence : configuredEssence.substring(0, slash);
        this.configuredSubtype = slash < 0 ? "" : configuredEssence.substring(slash + 1);
    }

    boolean isExact(String declared) {
        return essence(declared).equals(configuredEssence);
    }

    boolean matches(String declared) {
        String declaredEssence = essence(declared);
        if (declaredEssence.equals(configuredEssence)) {
            return true;
        }
        int plus = declaredEssence.lastIndexOf('+');
        return plus > 0
            && declaredEssence.startsWith(configuredType + "/")
            && declaredEssence.substring(plus + 1).equals(configuredSubtype);
    }

    /** The type/subtype pair without parameters, lower-cased. */
    private static String essence(String mediaType) {
        int semicolon = mediaType.indexOf(';');
        String essence = semicolon < 0 ? mediaType : mediaType.substring(0, semicolon);
        return essence.strip().toLowerCase(Locale.ROOT);
    }
}
