package com.consid.automation.camunda.internal.openapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTypeMatcherTest {

    private final MediaTypeMatcher matcher = new MediaTypeMatcher("application/json");

    @ParameterizedTest
    @ValueSource(strings = {
        "application/json",
        "Application/JSON",
        "application/json; charset=utf-8",
        " application/json ;q=0.9",
        "application/vnd.acme+json",
        "application/problem+json; charset=utf-8"
    })
    void test_matches_does_accept_equivalent_and_suffixed_types(String declared) {
        assertThat(matcher.matches(declared)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "application/xml",
        "text/json",
        "application/jsonx",
        "application/json+xml",
        "multipart/form-data"
    })
    void test_matches_does_reject_other_types(String declared) {
        assertThat(matcher.matches(declared)).isFalse();
    }

    @Test
    void test_is_exact_does_distinguish_suffix_match_from_exact_match() {
        assertThat(matcher.isExact("application/json; charset=utf-8")).isTrue();
        assertThat(matcher.isExact("application/vnd.acme+json")).isFalse();
    }

    @Test
    void test_suffixed_configured_type_does_only_match_itself() {
        // given
        MediaTypeMatcher vendor = new MediaTypeMatcher("application/vnd.acme+json");

        // then
        assertThat(vendor.matches("application/vnd.acme+json")).isTrue();
        assertThat(vendor.matches("application/json")).isFalse();
    }
}
