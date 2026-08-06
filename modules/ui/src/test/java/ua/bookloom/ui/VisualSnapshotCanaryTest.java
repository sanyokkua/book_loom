package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The {@code visual} canary: proof that the third local-only tag is partitioned exactly like the other two.
 *
 * <p>Visual validation snapshots a rendered screen and diffs it against a committed baseline with a tolerance. A
 * pixel baseline is only meaningful in a pinned environment — same OS, same fonts, same scaling — so the set is
 * kept out of the merge gate and run nightly or on demand
 * ({@code docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#visual-validation}, DD-35). Structural and
 * looked-up-colour conformance against {@code docs/specification/mockups/ui-mockup.html} is the gating check
 * instead, and that one is untagged and runs in {@code check}.
 *
 * <p><strong>Required environment:</strong> {@code BOOKLOOM_VISUAL_BASELINE_DIR} — the directory holding the
 * baselines captured on the pinned environment.
 */
@Tag("visual")
class VisualSnapshotCanaryTest {

    // Covers task 5.3/5.5: the `visual` task reaches a `visual`-tagged test when a baseline directory is
    // configured, and skips cleanly when the variable is absent.
    @Test
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_VISUAL_BASELINE_DIR", matches = "\\S+")
    void visual_baselineDirectoryConfigured_resolvesAnExistingDirectory() {
        final Path baselines = Path.of(System.getenv("BOOKLOOM_VISUAL_BASELINE_DIR"));

        assertThat(Files.isDirectory(baselines))
                .as("BOOKLOOM_VISUAL_BASELINE_DIR must point at the pinned-environment baseline directory")
                .isTrue();
    }
}
