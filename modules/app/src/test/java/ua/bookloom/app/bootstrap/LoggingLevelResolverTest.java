package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;
import ua.bookloom.util.paths.AppEnvironment;

class LoggingLevelResolverTest {

    @ParameterizedTest
    @MethodSource("precedenceCases")
    void resolve_precedenceAndDefaults_chooseTheExpectedLevel(
            AppEnvironment environment,
            Map<String, String> env,
            Map<String, String> properties,
            Level expectedLevel,
            ResolvedLogLevel.Source expectedSource) {
        final ResolvedLogLevel resolved = LoggingLevelResolver.resolve(lookup(env), lookup(properties), environment);

        assertThat(resolved.level()).isEqualTo(expectedLevel);
        assertThat(resolved.source()).isEqualTo(expectedSource);
        assertThat(resolved.rejectedValue()).isEmpty();
    }

    private static Stream<Arguments> precedenceCases() {
        return Stream.of(
                Arguments.of(
                        AppEnvironment.PROD,
                        Map.of("BOOKLOOM_LOG_LEVEL", "  tRaCe "),
                        Map.of("bookloom.log.level", "ERROR"),
                        Level.TRACE,
                        ResolvedLogLevel.Source.ENVIRONMENT),
                Arguments.of(
                        AppEnvironment.DEV,
                        Map.of(),
                        Map.of("bookloom.log.level", "  warn "),
                        Level.WARN,
                        ResolvedLogLevel.Source.PROPERTY),
                Arguments.of(AppEnvironment.DEV, Map.of(), Map.of(), Level.DEBUG, ResolvedLogLevel.Source.DEFAULT),
                Arguments.of(AppEnvironment.PROD, Map.of(), Map.of(), Level.INFO, ResolvedLogLevel.Source.DEFAULT));
    }

    @ParameterizedTest
    @MethodSource("invalidCases")
    void resolve_invalidHighestPrecedenceInput_usesEnvironmentDefaultAndRetainsValue(
            AppEnvironment environment,
            Map<String, String> env,
            Map<String, String> properties,
            Level expectedLevel,
            String rejectedValue) {
        final ResolvedLogLevel resolved = LoggingLevelResolver.resolve(lookup(env), lookup(properties), environment);

        assertThat(resolved.level()).isEqualTo(expectedLevel);
        assertThat(resolved.source()).isEqualTo(ResolvedLogLevel.Source.DEFAULT);
        assertThat(resolved.rejectedValue()).contains(rejectedValue);
    }

    private static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of(
                        AppEnvironment.DEV,
                        Map.of("BOOKLOOM_LOG_LEVEL", " LOUD "),
                        Map.of("bookloom.log.level", "ERROR"),
                        Level.DEBUG,
                        "LOUD"),
                Arguments.of(
                        AppEnvironment.DEV,
                        Map.of("BOOKLOOM_LOG_LEVEL", "   "),
                        Map.of("bookloom.log.level", "ERROR"),
                        Level.DEBUG,
                        ""),
                Arguments.of(AppEnvironment.PROD, Map.of(), Map.of("bookloom.log.level", " \t "), Level.INFO, ""),
                Arguments.of(AppEnvironment.PROD, Map.of(), Map.of("bookloom.log.level", "LOUD"), Level.INFO, "LOUD"));
    }

    private static Function<String, String> lookup(Map<String, String> values) {
        return values::get;
    }
}
