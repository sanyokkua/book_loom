package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;

/** The one line a desk row reads: why, then the text, with no placeholders in it. */
class ReviewRowLineTest {

    private static SegmentView withTarget(final @Nullable String machine) {
        final SegmentView base = ReviewFixtures.nameIssue();
        return new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                base.maskedSource(),
                base.displaySource(),
                machine,
                null,
                null,
                base.findings(),
                base.judgeScore(),
                base.path(),
                base.reviewed(),
                null,
                null);
    }

    // IF the row showed the placeholders, THEN the person would read ⟦0⟧ instead of a sentence; both token spellings
    // go.
    @ParameterizedTest
    @CsvSource({"Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері.", "Гейл відчинив ⟦0⟧старі⟦1⟧ двері."})
    void of_glossaryFindingAndMaskedTarget_lineIsWhyThenTheTextWithoutTokens(final String masked) {
        final ReviewRow row = ReviewRow.of(withTarget(masked), false);

        assertThat(row.line()).isEqualTo("name · Гейл відчинив старі двері.");
        assertThat(row.fullText()).isEqualTo("Гейл відчинив старі двері.");
    }

    // IF the person's own edit were not what the row reads, THEN the list would show text they replaced.
    @Test
    void of_segmentWithTheirEdit_lineReadsTheEdit() {
        final ReviewRow row = ReviewRow.of(ReviewFixtures.withUserEdit(), false);

        assertThat(row.line()).isEqualTo("name · Гейл відчинив старі двері.");
    }

    // IF a segment with no translation showed a blank line, THEN the row would look empty; it reads the source.
    @Test
    void of_noTargetAtAll_lineReadsTheSourceWithoutTokens() {
        final ReviewRow row = ReviewRow.of(withTarget(null), false);

        assertThat(row.line()).isEqualTo("name · Gale opened the old door.");
    }

    @Test
    void of_flaggedSegmentLeftForLater_markerIsLaterAndLocatorKept() {
        final ReviewRow row = ReviewRow.of(ReviewFixtures.lowScore(), true);

        assertThat(row.marker()).isEqualTo(DeskMarker.LATER);
        assertThat(row.locator()).isEqualTo("ch5 · p12");
        assertThat(row.segmentId()).isEqualTo("ch05.xhtml:11");
    }
}
