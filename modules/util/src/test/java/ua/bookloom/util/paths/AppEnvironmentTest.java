package ua.bookloom.util.paths;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The dev/production decision — startup step 1.
 *
 * <p>Every case here feeds a map rather than touching the real environment, which is the property that makes both
 * environments provable from one process.
 */
class AppEnvironmentTest {

    private static AppEnvironment resolve(final Map<String, String> env, final Map<String, String> properties) {
        return AppEnvironment.resolve(env::get, properties::get);
    }

    // Covers: FR-PERSIST-06 — WHERE no build stamp identifies the run as packaged, the system SHALL resolve to the
    // development environment, so an un-stamped run cannot reach production data.
    @Test
    void resolve_noSignalsAtAll_isDev() {
        assertThat(resolve(Map.of(), Map.of())).isEqualTo(AppEnvironment.DEV);
    }

    @Test
    void resolve_envOverrideProd_isProd() {
        assertThat(resolve(Map.of("BOOKLOOM_ENV", "prod"), Map.of())).isEqualTo(AppEnvironment.PROD);
    }

    // The override is highest precedence: it must beat both lower signals at once, or it is not an override.
    @Test
    void resolve_envOverrideDevAgainstBothProdSignals_isDev() {
        final Map<String, String> properties = Map.of("bookloom.env", "prod", "jpackage.app-path", "/Applications/x");

        assertThat(resolve(Map.of("BOOKLOOM_ENV", "dev"), properties)).isEqualTo(AppEnvironment.DEV);
    }

    @Test
    void resolve_buildStampProd_isProd() {
        assertThat(resolve(Map.of(), Map.of("bookloom.env", "prod"))).isEqualTo(AppEnvironment.PROD);
    }

    @Test
    void resolve_jpackageLauncherPropertyOnly_isProd() {
        assertThat(resolve(Map.of(), Map.of("jpackage.app-path", "/Applications/BookLoom.app")))
                .isEqualTo(AppEnvironment.PROD);
    }

    // An unrecognised or empty value must not be read as production. Defaulting the other way would put a debug run
    // in the user's real data folder on a typo, which is the one outcome this decision exists to prevent.
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "production", "PRODUCTION", "true", "1", "staging"})
    void resolve_unrecognisedEnvValue_fallsThroughToDev(final String value) {
        assertThat(resolve(Map.of("BOOKLOOM_ENV", value), Map.of())).isEqualTo(AppEnvironment.DEV);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROD", "Prod", "prod"})
    void resolve_envOverrideCaseInsensitive_isProd(final String value) {
        assertThat(resolve(Map.of("BOOKLOOM_ENV", value), Map.of())).isEqualTo(AppEnvironment.PROD);
    }

    @Test
    void isDev_devConstant_isTrueAndProdIsNot() {
        assertThat(AppEnvironment.DEV.isDev()).isTrue();
        assertThat(AppEnvironment.PROD.isDev()).isFalse();
    }
}
