package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewDeskFixtures;

/** The translation editor's marks in the window: shown by number, named in the strip, and holding the main button. */
class ReviewDeskMarksTest extends ReviewDeskTestBase {

    private static final String STRIP = "review-mark-strip";
    private static final String DISPLAYED = "Гейл відчинив ⟦0⟧старі⟦1⟧ двері.";

    private void edit(final int from, final int to, final String text) {
        onFx(() -> target().replaceText(from, to, text));
        awaitSettled();
    }

    private void awaitSettled() {
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String strip() {
        awaitSettled();
        return labelText(STRIP);
    }

    private void openMarked(final Locale locale) throws Exception {
        useLocale(locale);
        openDeskWith(ReviewDeskFixtures.sixToReview(), List.of());
    }

    // IF the stored ⟦g0⟧ form were shown, THEN the person would read an internal code in the book's text.
    @Test
    void desk_markedSegment_showsNumberedMarksInBothPanesAndTheOkNote() throws Exception {
        openMarked(Locale.ENGLISH);

        assertThat(fx(() -> target().getText())).isEqualTo(DISPLAYED);
        assertThat(fx(() -> ((TextArea) required("review-source")).getText()))
                .isEqualTo("Gale opened the ⟦0⟧old⟦1⟧ door.");
        assertThat(strip()).startsWith("Formatting marks ⟦0⟧ ⟦1⟧ keep bold, links and similar");
        assertThat(main().isDisabled()).isFalse();
    }

    // IF a deleted mark left the button on, THEN a broken edit could be saved; the strip says which mark and where.
    @Test
    void strip_markDeleted_namesItAndHoldsTheMainButton() throws Exception {
        openMarked(Locale.ENGLISH);

        edit(DISPLAYED.indexOf("⟦0⟧"), DISPLAYED.indexOf("⟦0⟧") + 3, "");

        assertThat(strip()).isEqualTo("⟦0⟧ is missing — paste it back where the formatting ends.");
        assertThat(main().isDisabled()).isTrue();
    }

    @Test
    void strip_markPastedBack_isTheOkNoteAndTheButtonIsOn() throws Exception {
        openMarked(Locale.ENGLISH);
        edit(DISPLAYED.indexOf("⟦0⟧"), DISPLAYED.indexOf("⟦0⟧") + 3, "");

        edit(DISPLAYED.indexOf("⟦0⟧"), DISPLAYED.indexOf("⟦0⟧"), "⟦0⟧");

        assertThat(strip()).startsWith("Formatting marks ⟦0⟧ ⟦1⟧ keep");
        assertThat(main().isDisabled()).isFalse();
    }

    // IF a hand-typed second mark passed, THEN the book would get the formatting twice.
    @Test
    void strip_markTypedByHandBesideThePresentOne_namesItAsDoubledAndHoldsTheButton() throws Exception {
        openMarked(Locale.ENGLISH);

        edit(0, 0, "⟦");
        edit(1, 1, "1");
        edit(2, 2, "⟧");

        assertThat(strip()).isEqualTo("⟦1⟧ appears twice — delete one of them.");
        assertThat(main().isDisabled()).isTrue();
    }

    @Test
    void strip_segmentWithoutMarks_saysSo() throws Exception {
        final SegmentView base = ReviewDeskFixtures.firstToReview();
        final SegmentView plain = new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                "Gale opened the door.",
                "Gale opened the door.",
                "Гейл відчинив двері.",
                null,
                null,
                base.findings(),
                base.judgeScore(),
                base.path(),
                base.reviewed(),
                null,
                null);
        useLocale(Locale.ENGLISH);

        openDeskWith(List.of(plain), List.of());

        assertThat(strip()).isEqualTo("No formatting marks in this segment.");
        assertThat(main().isDisabled()).isFalse();
    }

    // IF the Ukrainian wording were missing, THEN the strip would show a key name.
    @Test
    void strip_ukrainianMarkDeleted_readsInUkrainian() throws Exception {
        openMarked(Locale.forLanguageTag("uk"));

        edit(DISPLAYED.indexOf("⟦0⟧"), DISPLAYED.indexOf("⟦0⟧") + 3, "");

        assertThat(strip()).isEqualTo("Бракує ⟦0⟧ — вставте її назад там, де форматування закінчується.");
        assertThat(main().isDisabled()).isTrue();
    }

    // The banner with the Insert chips is gone: the strip and the protected editor replace it.
    @Test
    void desk_markedSegment_hasNoTokenBanner() throws Exception {
        openMarked(Locale.ENGLISH);
        edit(0, 0, "x");

        assertThat(fx(() -> target().getScene().getRoot().lookup("#review-token-banner")))
                .isNull();
    }
}
