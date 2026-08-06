package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The build-generated version string.
 *
 * <p><strong>What these prove and what they do not.</strong> {@link #current_thisBuild_reportsDev()} is a real
 * end-to-end check of the generation task: this test suite runs against a build with no {@code -PappVersion}, so the
 * resource on its classpath is one the Gradle task actually wrote. The tagged case cannot be checked the same way —
 * it would need a second Gradle build inside a unit test — so it is split: the reader is proved not to mangle a
 * tagged string here, and the task is proved to write the version verbatim by its own
 * {@code version=$resolvedAppVersion} template and the {@code inputs.property} that invalidates it on a bump. The
 * end-to-end tagged path belongs to the packaging launch smoke, which asserts the startup line of a real image.
 */
class AppVersionTest {

    private static String read(final String contents) {
        return AppVersion.read(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
    }

    // Covers: FR-UI-09 (partly — the startup log line only; the About dialog arrives with the UI) — WHERE a build
    // carries no injected version, the system SHALL report `dev`.
    @Test
    void current_thisBuild_reportsDev() {
        assertThat(AppVersion.current())
                .as("a build with no -PappVersion must say so; that string is how an artifact admits it did not "
                        + "come from the release pipeline (EC-REL-7)")
                .isEqualTo("dev");
    }

    /**
     * The suffix is the part that gets lost. A reader that split on {@code .} or parsed to a numeric triple would
     * turn {@code 1.2.0-rc1} into {@code 1.2.0} and publish a release candidate labelled as the release.
     */
    // Covers: FR-UI-09 (partly — the startup log line only) — WHEN a version is injected, the system SHALL report
    // it in full, including any pre-release suffix.
    @ParameterizedTest
    @ValueSource(strings = {"1.2.0-rc1", "1.2.0", "2.0.0-SNAPSHOT", "0.1.0-alpha.3+build.7"})
    void read_injectedVersion_roundTripsTheWholeString(final String version) {
        assertThat(read("version=" + version)).isEqualTo(version);
    }

    @Test
    void read_resourceAbsent_fallsBackToDev() {
        assertThat(AppVersion.read(null)).isEqualTo(AppVersion.FALLBACK);
    }

    @Test
    void read_keyMissing_fallsBackToDev() {
        assertThat(read("somethingElse=1.2.0")).isEqualTo(AppVersion.FALLBACK);
    }

    @Test
    void read_blankValue_fallsBackToDev() {
        assertThat(read("version=   ")).isEqualTo(AppVersion.FALLBACK);
    }
}
