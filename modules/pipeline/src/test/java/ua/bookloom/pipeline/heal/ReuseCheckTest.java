package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * {@link ReuseCheck#check}: a context-matched memory target is decided by the acceptance rule with no model call; one
 * that still carries a blocking finding is refused, so the segment is drafted instead.
 */
class ReuseCheckTest {

    private static final String SOURCE = "He bought a horse at the fair.";

    @TempDir
    private Path tempDir;

    @Test
    void reuse_contextMatchesButResidueLeft_isDraftedInstead() {
        // `ы` is a Russian letter left in a Ukrainian target: an `alphabet` finding at BLOCK level.
        final Result<DraftOutcome.Reused> reuse = check("Він купив кобылу на ярмарку.");

        assertThat(reuse.isErr()).isTrue();
        assertThat(reuse.error()).isNotNull();
        assertThat(reuse.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(reuse.error().details()).contains("language");
    }

    @Test
    void reuse_contextMatchesAndChecksPass_isReused() {
        final Result<DraftOutcome.Reused> reuse = check("Він купив коня на ярмарку.");

        assertThat(reuse.isOk()).isTrue();
        assertThat(reuse.data()).isNotNull();
        assertThat(reuse.data().restoredTarget()).isEqualTo("Він купив коня на ярмарку.");
    }

    private Result<DraftOutcome.Reused> check(final String storedTarget) {
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("book.md"), SOURCE);
        return ReuseCheck.check(
                segment,
                segment.masked(),
                List.of(),
                storedTarget,
                QualityLoopFixtures.PASSTHROUGH_GATE,
                QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.BALANCED));
    }
}
