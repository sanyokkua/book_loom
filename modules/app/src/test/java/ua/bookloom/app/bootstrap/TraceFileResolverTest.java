package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.util.paths.AppEnvironment;

/** Whether the detailed diagnostic log is written: on in a development run, off in an installed app unless asked. */
class TraceFileResolverTest {

    @ParameterizedTest(name = "{0} defaults to {1}")
    @CsvSource({"DEV, true", "PROD, false"})
    void resolve_nothingConfigured_followsTheEnvironment(final AppEnvironment environment, final boolean expected) {
        final ResolvedTraceFile resolved =
                TraceFileResolver.resolve(Map.<String, String>of()::get, Map.<String, String>of()::get, environment);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(expected, ResolvedLogLevel.Source.DEFAULT, null));
    }

    @ParameterizedTest(name = "BOOKLOOM_TRACE_FILE={0} gives {1}")
    @CsvSource({"1, true", "TRUE, true", " on , true", "yes, true", "0, false", "off, false", "False, false"})
    void resolve_environmentVariable_decidesInAnInstalledApp(final String value, final boolean expected) {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", value)::get, Map.<String, String>of()::get, AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(expected, ResolvedLogLevel.Source.ENVIRONMENT, null));
    }

    @Test
    void resolve_systemProperty_switchesItOnWhenNoVariableIsSet() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.<String, String>of()::get, Map.of("bookloom.trace.file", "true")::get, AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(true, ResolvedLogLevel.Source.PROPERTY, null));
    }

    @Test
    void resolve_variableAndProperty_theVariableWins() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "0")::get,
                Map.of("bookloom.trace.file", "true")::get,
                AppEnvironment.DEV);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedLogLevel.Source.ENVIRONMENT, null));
    }

    @Test
    void resolve_unknownValue_fallsBackToTheDefaultAndKeepsTheValueForTheWarning() {
        final ResolvedTraceFile resolved = TraceFileResolver.resolve(
                Map.of("BOOKLOOM_TRACE_FILE", "loud")::get, Map.<String, String>of()::get, AppEnvironment.PROD);

        assertThat(resolved).isEqualTo(new ResolvedTraceFile(false, ResolvedLogLevel.Source.DEFAULT, "loud"));
    }
}
