package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.LiveRow;

/**
 * A kept chapter summary is a row of the live card, {@code Chapter {n} summarised}, whose detail shows its text; the
 * scene-summary line that once sat under the detail is gone. The events reach the card the way a run's do: through
 * the job's listener, the run session and the mirror.
 */
class LiveCardSummaryRowTest extends LiveCardTestBase {

    private static final String SUMMARY = "Він знайшов листа. Вона поїхала до міста.";
    private static final String ROW_WORDS = "Chapter 3 summarised";
    private static final String LONG = "Коли він приземлився на дах старої вежі, місто вже спало під дощем."
            + " Вона чекала на нього біля воріт і тримала в руках лист, якого ніхто не мав прочитати."
            + " Разом вони вирішили піти до міста ще до світанку, щоб ніхто не помітив, куди вони зникли.";

    private void runChapterThree() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        job.emit(new SegmentStarted("s-1", "ch3 · p01", "He found the letter.", new ChunkPosition(3, 5, 1, 1)));
        job.emit(new SegmentDecided(
                "s-1",
                SegmentStatus.ACCEPTED,
                null,
                ProgressFixtures.progress(3, 5, 1, 0, 9),
                new SegmentDetail(null, SegmentPath.DRAFT, List.of(), "Він знайшов листа.")));
    }

    // IF a kept summary did not show as a row with its text, THEN the person could not read what the model will be
    // told about the chapter before it shapes the rest of the book.
    @Test
    void summaryKept_rowShowsText() throws Exception {
        runChapterThree();

        job.emit(new ChapterSummaryKept("unit-3#0", 3, 2, SUMMARY));
        awaitFx(() -> locators().contains(ROW_WORDS));
        pick(1);

        assertThat(locators()).containsExactly("ch3 · p01", ROW_WORDS);
        assertThat(ThemeTestSupport.onFx(this::secondRowText)).isEqualTo(SUMMARY);
        assertThat(((Label) required("translating-live-detail-summary")).getText())
                .isEqualTo(SUMMARY);
        assertThat(isShown("translating-live-detail-texts")).isFalse();
        assertThat(optional("translating-live-summary")).isNull();
    }

    // IF a run with the switch off showed a summary row, THEN the card would claim a summary no draft is given; a run
    // whose switch is off sends no kept summary, so its card lists only segments and no scene-summary line.
    @Test
    void switchOff_noRow() throws Exception {
        runChapterThree();

        awaitFx(() -> locators().contains("ch3 · p01"));

        assertThat(locators()).containsExactly("ch3 · p01");
        assertThat(optional("translating-live-summary")).isNull();
        assertThat(optional("translating-live-summary-text")).isNull();
    }

    // IF the summary's three sentences were cut at the smallest window in the longer language, THEN the person would
    // read half of what the model is told; the detail wraps them whole and the list keeps its row to one line.
    @Test
    void summaryRow_ukrainianAtTheSmallestWindow_detailShowsEveryLine() {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(960, 640);
        showRows(List.of(LiveRow.chapterSummary(new ChapterSummaryKept("unit-12#0", 12, 3, LONG))));
        pick(0);
        onFx(() -> {});

        assertThat(locators()).containsExactly("Розділ 12 підсумовано");
        assertThat(((Label) required("translating-live-detail-summary")).getText())
                .isEqualTo(LONG);
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(required(DETAIL))))
                .isEmpty();
    }

    // IF the chip sat past the right edge of the list's narrowest row, THEN a person would read "summary ke" and not
    // see that the summary was kept; at the list's smallest width the locator gives way and the chip stays whole.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void summaryRow_smallestList_chipStaysInsideTheRow(final String language) {
        useLocale(Locale.forLanguageTag(language));
        resizeScene(960, 640);
        showRows(List.of(LiveRow.chapterSummary(new ChapterSummaryKept("unit-12#0", 12, 3, LONG))));
        onFx(() -> {});

        final boolean inside = ThemeTestSupport.onFx(() -> {
            final Node chip = cells().getFirst().lookup(".state-marker");
            final Bounds cell =
                    cells().getFirst().localToScene(cells().getFirst().getLayoutBounds());
            return chip.localToScene(chip.getLayoutBounds()).getMaxX() <= cell.getMaxX() + 0.5;
        });

        assertThat(inside).isTrue();
        assertThat(ThemeTestSupport.onFx(
                        () -> FitProbe.cutParts(cells().getFirst().lookup(".state-marker"))))
                .isEmpty();
    }

    private String secondRowText() {
        return ((Labeled) cells().get(1).lookup(".live-row-text")).getText();
    }
}
