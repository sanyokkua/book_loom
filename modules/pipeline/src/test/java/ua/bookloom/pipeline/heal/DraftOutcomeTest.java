package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/**
 * {@link DraftOutcome.Drafted}: {@code restoredTarget} and {@code gateFinding} must be exactly one null and one
 * non-null — the gate either passed (a target, no finding) or it didn't (no target, a finding), never both or
 * neither, so {@link QualityLoop#start} never has to guess which one is missing. {@code maskedForm} is null exactly
 * when {@code restoredTarget} is: the two are the two outputs of one successful gate call.
 */
class DraftOutcomeTest {

    private static final String SOURCE = "He opened the old door.";

    @Test
    void constructor_bothRestoredTargetAndGateFindingPresent_throws() {
        assertThatThrownBy(() -> new DraftOutcome.Drafted(
                        segment(), SOURCE, List.of(), SOURCE, SOURCE, SOURCE, placeholderFinding()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_neitherRestoredTargetNorGateFindingPresent_throws() {
        assertThatThrownBy(() -> new DraftOutcome.Drafted(segment(), SOURCE, List.of(), SOURCE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_restoredTargetWithoutMaskedForm_throws() {
        assertThatThrownBy(() -> new DraftOutcome.Drafted(segment(), SOURCE, List.of(), SOURCE, null, SOURCE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_maskedFormWithoutRestoredTarget_throws() {
        assertThatThrownBy(() -> new DraftOutcome.Drafted(
                        segment(), SOURCE, List.of(), SOURCE, SOURCE, null, placeholderFinding()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_maskedFormAndRestoredTargetTogether_isAccepted() {
        final DraftOutcome.Drafted drafted =
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), "reply", "form", SOURCE, null);

        assertThat(drafted.maskedForm()).isEqualTo("form");
    }

    private static QaFinding placeholderFinding() {
        return new QaFinding("markup", Severity.HIGH, "placeholder token mismatch", "placeholder");
    }

    private static Segment segment() {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                SOURCE,
                SOURCE,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
