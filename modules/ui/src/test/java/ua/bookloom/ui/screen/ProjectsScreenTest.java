package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.SegmentedButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The Projects screen the application builds, read from the real scene: the list grouped by book, what a row says, the
 * search and filter, and the first-launch card. What the buttons do is in {@link ProjectsActionsTest}.
 */
class ProjectsScreenTest extends ProjectsScreenTestBase {

    private static String hash8(final String hash) {
        return hash.substring(0, 8);
    }

    // IF translations of one book were not gathered under one header, THEN the person could not see how many
    // translations a book has.
    @Test
    void list_twoTranslationsOfOneBook_showsOneHeaderWithFileFormatSegmentsAndCount() throws Exception {
        showListing(ukrainianRunning(), germanNamed(), tideExported());

        assertThat(textOf("projects-book-" + hash8(ua.bookloom.ui.ProjectFixtures.BOOK_HASH)))
                .contains("earth-gravity.epub · EPUB · 1,240 segments")
                .contains("2 translations")
                .contains("＋ Translate into another language");
        assertThat(textOf("projects-book-" + hash8(TIDE_HASH))).contains("1 translation");
        assertThat(isPresent("projects-row-" + EARTH_ID)).isTrue();
        assertThat(isPresent("projects-row-" + GERMAN_ID)).isTrue();
        assertThat(isPresent("projects-row-" + TIDE_ID)).isTrue();
    }

    // IF the segment count on a book's header carried no explanation, THEN "1,240 segments" would be a riddle.
    @Test
    void list_bookHeader_segmentCountExplainsWhatASegmentIs() throws Exception {
        showListing(ukrainianRunning());

        assertThat(ThemeTestSupport.onFx(() -> ua.bookloom.ui.TooltipProbe.tipText(
                        node("projects-book-meta-" + hash8(ua.bookloom.ui.ProjectFixtures.BOOK_HASH)))))
                .isEqualTo("A segment is a paragraph, heading or caption — one piece of the book's text.");
    }

    // IF a row hid its pair, model, quality or progress, THEN two translations of a book could not be told apart.
    @Test
    void list_pausedRow_showsPairModelQualityProgressWhenAndThePausedChip() throws Exception {
        showListing(ukrainianRunning());

        final String row = textOf("projects-row-" + EARTH_ID);

        assertThat(row)
                .contains("Earth Gravity")
                .contains(" · EN → UK")
                .contains("EN → UK · Ollama · gemma4:e4b · Balanced")
                .contains("51 % · yesterday")
                .contains("Paused")
                .doesNotContain("Translating");
        assertThat(continueButton(EARTH_ID).getText()).isEqualTo("Continue");
    }

    // IF a long book title were cut with no tooltip, THEN two books with one opening could not be told apart.
    @Test
    void list_longBookTitle_isCutWithTheWholeTitleInItsTooltip() throws Exception {
        final ProjectSummary base = ukrainianRunning();
        final Project stored = base.project();
        final String title =
                "A Very Long Name That Goes On And On Through Every Word A Title Could Hold Until No Line Can Fit It At All";
        showListing(ProjectFixtures.summary(
                new Project(
                        stored.id(),
                        stored.source(),
                        stored.format(),
                        stored.contentHash(),
                        stored.brief(),
                        stored.name(),
                        stored.nameOrigin(),
                        title,
                        stored.createdAt(),
                        stored.lastOpenedAt()),
                base.decidedSegments(),
                base.latestRun(),
                null));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(ThemeTestSupport.onFx(() -> ua.bookloom.ui.FitProbe.cutParts(scene.getRoot())))
                .doesNotContain(title);
        assertThat(ThemeTestSupport.onFx(() -> ua.bookloom.ui.TooltipProbe.tipText(
                        node("projects-book-title-" + hash8(ua.bookloom.ui.ProjectFixtures.BOOK_HASH)))))
                .isEqualTo(title);
    }

    private static ProjectSummary exported(final String id, final String name) {
        final java.time.Instant opened = java.time.Instant.parse("2026-10-21T10:00:00Z");
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        id,
                        ProjectFixtures.BOOK_FILE,
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        ua.bookloom.api.project.NameOrigin.AUTO,
                        name,
                        opened),
                ProjectFixtures.BOOK_SEGMENTS,
                ProjectFixtures.run(id, ua.bookloom.api.pipeline.JobState.COMPLETED),
                ProjectFixtures.export(opened));
    }

    private List<javafx.scene.control.Label> labelsIn(final String id, final String text) {
        return node(id).lookupAll(".label").stream()
                .map(javafx.scene.control.Label.class::cast)
                .filter(label -> text.equals(label.getText()))
                .toList();
    }

    // IF a short row cut the end of its name, THEN "earth-gravity · EN → UK" would lose its language pair.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void list_smallestWindowOpenProject_showsTheWholeName(final String tag) throws Exception {
        useLocale(Locale.forLanguageTag(tag));
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        final String name = "earth-gravity · EN → UK";
        onFx(() -> ua.bookloom.ui.state.OpenBookForTest.open(
                injector.getInstance(ua.bookloom.ui.state.CurrentProject.class), "en", "uk"));
        showListing(exported("p1", name));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(ThemeTestSupport.onFx(() -> labelsIn("projects-row-p1", " · EN → UK").stream()
                        .map(label -> label.getWidth() + 0.5 >= label.prefWidth(-1))
                        .toList()))
                .as("the language pair is whole")
                .containsExactly(true);
        assertThat(ThemeTestSupport.onFx(() -> labelsIn("projects-row-p1", "earth-gravity").stream()
                        .map(label -> label.getWidth() + 0.5 >= label.prefWidth(-1)
                                || name.equals(ua.bookloom.ui.TooltipProbe.tipText(label)))
                        .toList()))
                .as("the title is whole, or cut with the whole name as its tooltip")
                .containsExactly(true);
    }

    // IF the ⋯ menu offered another set, THEN the row actions of the specification would not all be reachable.
    @Test
    void list_row_moreMenuOffersRenameShowBookFileAndRemove() throws Exception {
        showListing(ukrainianRunning());

        assertThat(menuCaptions(EARTH_ID)).containsExactly("Rename...", "Show book file", "Remove project...");
    }

    // IF a project that was never run said it was translating, THEN its row would promise work that has not begun.
    @Test
    void list_neverRunRow_saysNotStartedWithNoModelAndZeroPercent() throws Exception {
        showListing(germanNamed());

        assertThat(textOf("projects-row-" + GERMAN_ID))
                .contains("EN → DE · not started")
                .contains("Not started")
                .contains("0 %")
                .doesNotContain("gemma4:e4b");
    }

    @Test
    void list_namedProject_carriesTheNamedChipAndAnAutomaticOneDoesNot() throws Exception {
        showListing(ukrainianRunning(), germanNamed());

        assertThat(textOf("projects-row-" + GERMAN_ID)).contains("named");
        assertThat(textOf("projects-row-" + EARTH_ID)).doesNotContain("named");
    }

    @Test
    void list_exportedProject_saysExportedAndOffersOpen() throws Exception {
        showListing(tideExported());

        assertThat(textOf("projects-row-" + TIDE_ID)).contains("Exported");
        assertThat(continueButton(TIDE_ID).getText()).isEqualTo("Open");
    }

    // IF the project that is on screen offered Continue, THEN pressing it would reopen what is already open.
    @Test
    void list_openProject_carriesTheOpenChip() throws Exception {
        onFx(() -> ua.bookloom.ui.state.OpenBookForTest.open(
                injector.getInstance(ua.bookloom.ui.state.CurrentProject.class), "en", "uk"));
        showListing(openProjectRow());

        assertThat(textOf("projects-row-p1")).contains("open");
        assertThat(continueButton("p1").getText()).isEqualTo("Open");
    }

    private static ua.bookloom.api.pipeline.ProjectSummary openProjectRow() {
        return ua.bookloom.ui.ProjectFixtures.summary(
                ua.bookloom.ui.ProjectFixtures.project(
                        "p1",
                        "earth-gravity.epub",
                        ua.bookloom.ui.ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        ua.bookloom.api.project.NameOrigin.AUTO,
                        "Earth Gravity · EN → UK",
                        java.time.Instant.parse("2026-10-21T10:00:00Z")),
                0,
                null,
                null);
    }

    // IF the screen listed on the FX thread, THEN hashing every book file and reading every segment would freeze the
    // window; the list is asked for off it.
    @Test
    void list_whenShown_isAskedForOffTheFxThread() throws Exception {
        showListing(ukrainianRunning());

        assertThat(projects.listCallsOnFxThread()).isNotEmpty().doesNotContain(true);
    }

    private void searchFor(final String text) {
        onFx(() -> ((TextField) node("projects-search")).setText(text));
    }

    // IF a search with no hits showed an empty page, THEN the person could not tell the list from a failure, nor get
    // back; the line names the text and Clear search restores every row.
    @Test
    void search_noHits_saysNoProjectMatchesAndClearSearchRestoresTheList() throws Exception {
        showListing(ukrainianRunning(), tideExported());

        searchFor("xyz");

        assertThat(textOf("projects-no-match")).contains("No project matches 'xyz'.");
        assertThat(isPresent("projects-row-" + EARTH_ID)).isFalse();
        onFx(() -> ((Hyperlink) node("projects-clear-search")).fire());
        assertThat(isPresent("projects-no-match")).isFalse();
        assertThat(isPresent("projects-row-" + EARTH_ID)).isTrue();
        assertThat(isPresent("projects-row-" + TIDE_ID)).isTrue();
        assertThat(((TextField) node("projects-search")).getText()).isEmpty();
    }

    // IF search looked only at project names, THEN a book could not be found by its file name or title.
    @ParameterizedTest
    @ValueSource(strings = {"tide", "TIDE-TABLES.epub", "Tide Tables"})
    void search_bookFileOrTitle_keepsOnlyThatBook(final String text) throws Exception {
        showListing(ukrainianRunning(), tideExported());

        searchFor(text);

        assertThat(isPresent("projects-row-" + TIDE_ID)).isTrue();
        assertThat(isPresent("projects-row-" + EARTH_ID)).isFalse();
    }

    private void chooseFilter(final String caption) {
        onFx(() -> ((SegmentedButton) node("projects-filter"))
                .getButtons().stream()
                        .filter(button -> caption.equals(button.getText()))
                        .map(ToggleButton.class::cast)
                        .findFirst()
                        .orElseThrow()
                        .fire());
    }

    // IF Finished kept a running project, or In progress dropped a failed or never-run one, THEN the filter would
    // contradict the status chips.
    @Test
    void filter_finishedAndInProgress_splitTheRowsByStatus() throws Exception {
        showListing(ukrainianRunning(), germanNamed(), tideExported());

        chooseFilter("Finished");
        assertThat(isPresent("projects-row-" + TIDE_ID)).isTrue();
        assertThat(isPresent("projects-row-" + EARTH_ID)).isFalse();
        assertThat(isPresent("projects-row-" + GERMAN_ID)).isFalse();

        chooseFilter("In progress");
        assertThat(isPresent("projects-row-" + TIDE_ID)).isFalse();
        assertThat(isPresent("projects-row-" + EARTH_ID)).isTrue();
        assertThat(isPresent("projects-row-" + GERMAN_ID)).isTrue();

        chooseFilter("All");
        assertThat(isPresent("projects-row-" + TIDE_ID)).isTrue();
        assertThat(isPresent("projects-row-" + EARTH_ID)).isTrue();
    }

    @Test
    void list_withProjects_showsTheNewTranslationToolbarButton() throws Exception {
        showListing(ukrainianRunning());

        assertThat(((Button) node("projects-new")).getText()).isEqualTo("＋ New translation");
    }

    // IF the first-launch card kept the list's search, filter or toolbar button, THEN a person with nothing stored
    // would be offered to search nothing and to start twice; the card has its own start button.
    @Test
    void empty_noProjects_showsTheHowItWorksCardWithItsOwnStartAndNoListControls() throws Exception {
        showEmptyListing();

        assertThat(textOf("projects-empty-card"))
                .contains("Translate a book on your own computer")
                .contains("Choose a book (EPUB, FB2, Markdown or plain text).")
                .contains("Check the languages. The rest is filled in for you.")
                .contains("Let it translate. A book takes hours; you can stop and continue at any time.")
                .contains("Save the translated book. Your original file is never changed.");
        assertThat(((Button) node("projects-empty-new")).getText()).isEqualTo("＋ New translation");
        assertThat(isPresent("projects-search")).isFalse();
        assertThat(isPresent("projects-filter")).isFalse();
        assertThat(isPresent("projects-new")).isFalse();
    }

    // IF the card's model line did not follow the chosen model, THEN it would say ready for a program nobody chose.
    @Test
    void empty_modelChosen_saysTheModelIsReadyAndOffersChange() throws Exception {
        showEmptyListing();
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        ImportScreenTestBase.awaitFx(
                () -> injector.getInstance(RunStateSource.class).model().get().chosen());

        assertThat(textOf("projects-empty-card"))
                .contains("AI model: Ollama · gemma3:12b — ready ✓")
                .contains("Change...")
                .doesNotContain("I use another program...");
    }

    @Test
    void empty_noModel_saysNoAiModelFoundAndOffersAnotherProgram() throws Exception {
        showEmptyListing();

        assertThat(textOf("projects-empty-card"))
                .contains("No AI model found")
                .contains("I use another program...")
                .doesNotContain("ready ✓");
    }

    // IF the screen's words were not in the catalogue for Ukrainian, THEN a Ukrainian session would see English.
    @Test
    void list_underUkrainian_headerAndRowAreInUkrainian() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        showListing(ukrainianRunning());

        assertThat(textOf("projects-book-" + hash8(ua.bookloom.ui.ProjectFixtures.BOOK_HASH)))
                .contains("earth-gravity.epub · EPUB")
                .contains("сегмент")
                .contains("Перекласти іншою мовою");
        assertThat(textOf("projects-row-" + EARTH_ID)).contains("учора").contains("На паузі");
        assertThat(ThemeTestSupport.onFx(() -> List.of(continueButton(EARTH_ID).getText())))
                .containsExactly("Продовжити");
    }
}
