package com.consid.automation.camunda;

import org.apache.maven.plugin.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal {@link Log} that records messages so Mojo tests can assert on them
 * without a mocking framework.
 */
final class RecordingLog implements Log {

    final List<String> infos = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    @Override public boolean isDebugEnabled() { return false; }
    @Override public void debug(CharSequence content) { }
    @Override public void debug(CharSequence content, Throwable error) { }
    @Override public void debug(Throwable error) { }

    @Override public boolean isInfoEnabled() { return true; }
    @Override public void info(CharSequence content) { infos.add(content.toString()); }
    @Override public void info(CharSequence content, Throwable error) { infos.add(content.toString()); }
    @Override public void info(Throwable error) { infos.add(String.valueOf(error)); }

    @Override public boolean isWarnEnabled() { return true; }
    @Override public void warn(CharSequence content) { warnings.add(content.toString()); }
    @Override public void warn(CharSequence content, Throwable error) { warnings.add(content.toString()); }
    @Override public void warn(Throwable error) { warnings.add(String.valueOf(error)); }

    @Override public boolean isErrorEnabled() { return true; }
    @Override public void error(CharSequence content) { errors.add(content.toString()); }
    @Override public void error(CharSequence content, Throwable error) { errors.add(content.toString()); }
    @Override public void error(Throwable error) { errors.add(String.valueOf(error)); }
}
