package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The two cards at the top of the import screen once a book is open, read from the real scene. Book details owns what
 * identifies the book (file, format, title and author, declared language, chapters with the words, DRM); Book facts
 * owns every count the translation depends on and the checks, in one card. Each figure is shown by exactly one of them.
 */
class ImportBookFactsTest extends ImportBookTestBase {

    private static final List<String> DETAILS_ROWS =
            List.of("file", "format", "titleAuthor", "declaredLang", "chapters", "drm");

    private static final List<String> FACT_IDS = List.of(
            "structure-stat-segments",
            "structure-stat-images",
            "structure-stat-code",
            "structure-stat-codeNote",
            "structure-stat-fonts",
            "structure-stat-verse",
            "structure-stat-notes",
            "structure-stat-formatting",
            "structure-check-roundtrip",
            "structure-check-ids");

    private void runTranslates(final int segments, final int keptAsSource) {
        ((ScriptedReviewDesk) injector.getInstance(ReviewDesk.class))
                .willAnswerCounts(new ReviewCounts(segments + keptAsSource, 0, 0, 0, 0, segments, keptAsSource, 0, 0));
    }

    // IF the details card carried a figure the facts card owns (or the reverse), THEN the screen would show a number
    // twice and the two could disagree.
    @Test
    void details_bookOpened_holdsTheIdentityRowsAndNoCountsOfMedia() throws TimeoutException {
        openFactsBook();

        assertThat(idsStartingWith("import-row-"))
                .containsExactlyInAnyOrderElementsOf(
                        DETAILS_ROWS.stream().map(row -> "import-row-" + row).toList());
        assertThat(optional("import-row-media")).isNull();
    }

    // IF a details row reported the wrong value, THEN the person would see a different book from the one they opened.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "file | book.epub",
                "format | EPUB",
                "titleAuthor | Title · Author",
                "declaredLang | English (en)",
                "chapters | 2 · about 78,200 words",
                "drm | none"
            })
    void details_bookOpened_rowShowsItsValue(final String row, final String expected) throws TimeoutException {
        openFactsBook();

        assertThat(textOf("import-row-" + row)).contains(expected);
    }

    // IF DRM were drawn as a warning or hidden on a book that opened, THEN the card would claim a protection the
    // inspection never found: a card exists only for a readable book, which has none.
    @Test
    void details_bookOpened_drmRowIsTheOkChipNone() throws TimeoutException {
        openFactsBook();

        assertThat(ThemeTestSupport.onFx(() -> required("import-row-drm").lookupAll(".chip-ok").stream()
                        .map(chip -> ((Label) chip).getText())
                        .toList()))
                .containsExactly("none");
    }

    // IF the facts were two cards or lacked a title, THEN the screen would not read as one summary of the book.
    @Test
    void facts_bookOpened_areOneTitledCardHoldingEveryCountAndBothChecks() throws TimeoutException {
        openCheckedFactsBook();

        assertThat(labelText("import-facts-title")).isEqualTo("Book facts");
        assertThat(FACT_IDS.stream()
                        .filter(id -> !isInside(id, "import-facts-card"))
                        .toList())
                .isEmpty();
        assertThat(optional("structure-stats-card")).isNull();
        assertThat(optional("structure-checks-card")).isNull();
    }

    // IF a count were read from the wrong statistic, THEN the person would judge the book by numbers it does not have.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "structure-stat-segments | 5",
                "structure-stat-images | 7",
                "structure-stat-code | 3",
                "structure-stat-codeNote | preserved — never translated",
                "structure-stat-fonts | 2",
                "structure-stat-verse | 12",
                "structure-stat-notes | 4 · 1",
                "structure-stat-formatting | italics, quotes → protected",
                "structure-check-roundtrip | Round-trip check passed — structure & text preserved",
                "structure-check-ids | All image / font / link IDs preserved"
            })
    void facts_bookOpened_figureShowsItsValue(final String id, final String expected) throws TimeoutException {
        openCheckedFactsBook();

        assertThat(labelText(id)).isEqualTo(expected);
    }

    // IF the words, the total or the media counts appeared in both cards, THEN a figure would be on screen twice.
    @Test
    void facts_bookOpened_doNotRepeatWhatTheDetailsOrTheTreeAlreadyShow() throws TimeoutException {
        openCheckedFactsBook();

        assertThat(optional("structure-stat-words")).isNull();
        assertThat(optional("structure-total")).isNull();
    }

    // IF a caption appeared in both cards (images and fonts were in details and facts), THEN the screen would show the
    // same figure under two names. Every caption below is matched in either card and must be found exactly once.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "images",
                "fonts",
                "words",
                "chapters",
                "segments",
                "code blocks",
                "verse",
                "footnotes",
                "inline formatting",
                "round-trip",
                "IDs",
                "declared language"
            })
    void figures_bookOpened_eachCaptionAppearsOnceAcrossTheTwoCards(final String fragment) throws TimeoutException {
        openCheckedFactsBook();

        assertThat(labelsContaining(fragment, "import-card", "import-facts-card"))
                .as("labels containing '%s' in the details and facts cards", fragment)
                .hasSize(1);
    }

    // IF one place on the page rounded the word count differently from another, THEN the page would give two figures
    // for one book; the verdict, the fold's summary and the chapters row all word it through the same owner.
    @Test
    void figures_bookOpened_everyPlaceTheWordCountAppearsSaysTheSameRoundedFigure() throws TimeoutException {
        openCheckedFactsBook();

        assertThat(labelsContaining("78,", "import-screen"))
                .hasSize(3)
                .allSatisfy(text -> assertThat(text).contains("about 78,200 words"));
    }

    // IF the run total were a second card or lost with the old Structure screen, THEN the person would not learn why
    // Translating counts more than the book text; it belongs to the segments fact it explains.
    @Test
    void runTotal_runTranslatesMoreThanTheBookText_isInTheFactsCard() throws TimeoutException {
        runTranslates(36, 0);

        openCheckedFactsBook();

        assertThat(labelText("structure-run-total"))
                .isEqualTo("5 segments in the book text + 31 other texts (titles, alt texts, contents, metadata)"
                        + " = 36 to translate");
        assertThat(isInside("structure-run-total", "import-facts-card")).isTrue();
    }

    // IF the run total showed when the run translates only the book text, THEN it would repeat the segments fact.
    @Test
    void runTotal_runTranslatesOnlyTheBookText_isNotShown() throws TimeoutException {
        runTranslates(5, 0);

        openCheckedFactsBook();

        assertThat(isShown("structure-run-total")).isFalse();
    }

    // IF a lost identifier were not named, THEN the person could not find what the export would break.
    @Test
    void checks_missingImageId_namesItInTheFactsCard() throws TimeoutException {
        openCheckedFactsBook(
                new RoundTripReport(true, false, List.of("img-7"), 5, 5), new BookPlan(Map.of(), List.of()));

        assertThat(labelText("structure-check-ids")).isEqualTo("IDs missing after the round trip: img-7");
        assertThat(isInside("structure-check-ids", "import-facts-card")).isTrue();
    }

    // IF a dropped paragraph passed silently, THEN hours of translation would end in a book missing text.
    @Test
    void checks_oneSegmentDropped_failsAndNamesBothCounts() throws TimeoutException {
        openCheckedFactsBook(new RoundTripReport(false, true, List.of(), 5, 4), new BookPlan(Map.of(), List.of()));

        assertThat(labelText("structure-check-roundtrip"))
                .isEqualTo("Round-trip check failed — structure not preserved (4 of 5 segments)");
    }

    // IF an oversized segment gave no warning, THEN the person would not know why a paragraph is translated in pieces.
    @Test
    void warning_oneOversizedSegment_isInTheFactsCard() throws TimeoutException {
        openCheckedFactsBook(FAITHFUL, new BookPlan(Map.of("unit-0", 3), List.of("s-2")));

        assertThat(labelText("structure-chunk-warning-text"))
                .isEqualTo("1 segment exceeds the group budget — it will be split by sentence and re-joined.");
        assertThat(isInside("structure-chunk-warning", "import-facts-card")).isTrue();
    }

    // IF a check that has not answered held Continue back, THEN a slow book would block the person from going on.
    @Test
    void continueControl_checksStillRunning_isAvailableAndTheRowSaysChecking() throws Exception {
        scriptChecksAndSegments(FAITHFUL, new BookPlan(Map.of(), List.of()));
        projects.holdRoundTrip();

        openFactsBookWithoutWaiting();
        projects.awaitRoundTripEntered();

        assertThat(labelText("structure-check-roundtrip")).startsWith("Checking");
        assertThat(button("import-continue").isDisabled()).isFalse();
        assertThat(labelText("structure-stat-segments")).isEqualTo("5");
        projects.releaseRoundTrip();
    }

    // IF the checks ran on the FX thread, twice, or for another project, THEN the window would freeze or check a stale
    // book.
    @Test
    void checks_bookOpened_askTheServiceOnceOffTheFxThreadForTheOpenProject() throws TimeoutException {
        openCheckedFactsBook();

        assertThat(projects.roundTripProjects()).containsExactly("p1");
        assertThat(projects.planProjects()).containsExactly("p1");
        assertThat(projects.roundTripCallsOnFxThread()).containsExactly(false);
        assertThat(projects.planCallsOnFxThread()).containsExactly(false);
    }

    // IF the new title were missing from Ukrainian, THEN a Ukrainian reader would see an English card heading.
    @Test
    void facts_ukrainianLocale_titleAndCaptionsAreUkrainian() throws TimeoutException {
        scriptChecksAndSegments(FAITHFUL, new BookPlan(Map.of(), List.of()));
        useLocale(Locale.forLanguageTag("uk"));

        openFactsBookWithoutWaiting();
        awaitFx(() -> optional("structure-check-roundtrip") != null);

        assertThat(labelText("import-facts-title")).isEqualTo("Факти про книгу");
        assertThat(labelsContaining("images", "import-facts-card")).isEmpty();
        assertThat(labelsContaining("fonts", "import-facts-card")).isEmpty();
    }
}
