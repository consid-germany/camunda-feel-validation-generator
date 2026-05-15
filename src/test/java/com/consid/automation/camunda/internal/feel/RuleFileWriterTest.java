package com.consid.automation.camunda.internal.feel;

import com.consid.automation.camunda.internal.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RuleFileWriter.
 */
class RuleFileWriterTest {

    @TempDir
    Path tempDir;

    private final RuleFileWriter writer = new RuleFileWriter();

    @Test
    void test_write_does_create_file_when_parent_exists_as_expected() throws IOException {
        // given
        Path target = tempDir.resolve("output.feel");

        // when
        writer.write(target, "content");

        // then
        assertThat(Files.readString(target)).isEqualTo("content");
    }

    @Test
    void test_write_does_create_missing_parent_directories_as_expected() throws IOException {
        // given
        Path target = tempDir.resolve("nested/dirs/output.feel");

        // when
        writer.write(target, "content");

        // then
        assertThat(Files.exists(target.getParent())).isTrue();
        assertThat(Files.readString(target)).isEqualTo("content");
    }

    @Test
    void test_write_does_overwrite_existing_file_as_expected() throws IOException {
        // given
        Path target = tempDir.resolve("output.feel");
        Files.writeString(target, "previous");

        // when
        writer.write(target, "replacement");

        // then
        assertThat(Files.readString(target)).isEqualTo("replacement");
    }
}
