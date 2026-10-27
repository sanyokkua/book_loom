package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.util.paths.AppEnvironment;

/** Whether the detailed diagnostic log is written: on in a development run, off in an installed app unless asked. */
class TraceFileResolverTest {

    @TempDir
    private Path dir;

    @ParameterizedTest(name = "{0} defaults to {1}")
    @CsvSource({"DEV, true", "PROD, false"})
    void resolve_nothingConfigured_followsTheEnvironment(final AppEnvironment environment, final boolean expected) {
        final ResolvedTraceFile resolved =
                TraceFileResolver.resolve(Map.<String, String>of()::get, Map.<String, String>of()::get, environment);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(expected, ResolvedTraceFile.Source.DEFAULT, null));
    }

    @ParameterizedTest(name = "BOOKLOOM_TRACE_FILE={0} gives {1}")
    @CsvSource({"1, true", "TRUE, true", " on , true", "yes, true", "0, false", "off, false", "False, false"})
    void resolve_environmentVariable_decidesInAnInstalledApp(final String value, final boolean expected) {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", value)::get, Map.<String, String>of()::get, AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(expected, ResolvedTraceFile.Source.ENVIRONMENT, null));
    }

    @Test
    void resolve_systemProperty_switchesItOnWhenNoVariableIsSet() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.<String, String>of()::get, Map.of("bookloom.trace.file", "true")::get, AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(true, ResolvedTraceFile.Source.PROPERTY, null));
    }

    @Test
    void resolve_variableAndProperty_theVariableWins() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "0")::get,
                Map.of("bookloom.trace.file", "true")::get,
                AppEnvironment.DEV);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedTraceFile.Source.ENVIRONMENT, null));
    }

    @Test
    void resolve_unknownValue_fallsBackToTheDefaultAndKeepsTheValueForTheWarning() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "loud")::get, Map.<String, String>of()::get, AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedTraceFile.Source.DEFAULT, "loud"));
    }

    private Path marker() throws IOException {
        return Files.writeString(dir.resolve("detailed-log.on"), "x");
    }

    @Test
    void resolve_markerPresentNothingSet_onFromMarker() throws IOException {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.<String, String>of()::get, Map.<String, String>of()::get, AppEnvironment.PROD, marker());

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(true, ResolvedTraceFile.Source.MARKER, null));
    }

    @Test
    void resolve_markerPresentEnvOff_offFromEnvironment() throws IOException {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "0")::get, Map.<String, String>of()::get, AppEnvironment.PROD, marker());

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedTraceFile.Source.ENVIRONMENT, null));
    }

    @Test
    void resolve_markerPresentPropertyFalse_offFromProperty() throws IOException {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.<String, String>of()::get,
                Map.of("bookloom.trace.file", "false")::get,
                AppEnvironment.PROD,
                marker());

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedTraceFile.Source.PROPERTY, null));
    }

    @Test
    void resolve_markerAbsentProduction_offDefault() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.<String, String>of()::get, Map.<String, String>of()::get, AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedTraceFile.Source.DEFAULT, null));
    }

    @Test
    void resolve_junkEnvMarkerPresent_onFromMarkerJunkKept() throws IOException {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "loud")::get,
                Map.<String, String>of()::get,
                AppEnvironment.PROD,
                marker());

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(true, ResolvedTraceFile.Source.MARKER, "loud"));
    }

    @Test
    void resolve_junkEnvPropertyDecides_junkStillKept() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "loud")::get,
                Map.of("bookloom.trace.file", "true")::get,
                AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(true, ResolvedTraceFile.Source.PROPERTY, "loud"));
    }

    @Test
    void resolve_bothJunk_bothKept() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "loud")::get,
                Map.of("bookloom.trace.file", "maybe")::get,
                AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedTraceFile.Source.DEFAULT, "loud, maybe"));
    }

    @Test
    void resolve_junkPropertyEnvDecides_junkStillKept() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "1")::get,
                Map.of("bookloom.trace.file", "maybe")::get,
                AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(true, ResolvedTraceFile.Source.ENVIRONMENT, "maybe"));
    }
}
