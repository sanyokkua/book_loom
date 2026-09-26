package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.RunState;

/**
 * The export screen as the completion report: what a finished run wrote, how it ended up, and the one thing a person
 * can do about it. The run writes the book as its last act, so nothing here may trigger a write.
 */
class ExportScreenTest extends TranslatingScreenTestBase {

    private static final Path WRITTEN = Path.of("/books/Frankenstein.uk.epub");

    private static JobReport epubReport() {
        return new JobReport(BookFormat.EPUB, JobState.COMPLETED, 1240, 1237, 3, List.of(), WRITTEN, null);
    }

    static Stream<Arguments> runsThatWroteNothing() {
        final JobReport cancelled =
                new JobReport(BookFormat.EPUB, JobState.CANCELLED, 1240, 600, 2, List.of(), null, null);
        final JobReport failed = new JobReport(
                BookFormat.EPUB,
                JobState.FAILED,
                1240,
                10,
                0,
                List.of(),
                null,
                AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing is listening at http://localhost:11434."));
        return Stream.of(
                Arguments.of(Named.of("a stopped run", cancelled), RunState.STOPPED),
                Arguments.of(Named.of("a failed run", failed), RunState.FAILED));
    }

    static Stream<Arguments> auxiliaryControls() {
        return Stream.of(
                Arguments.of("export-aux-glossary", CheckBox.class),
                Arguments.of("export-aux-bilingual", CheckBox.class),
                Arguments.of("export-aux-report", CheckBox.class),
                Arguments.of("export-aux-consistency", ToggleSwitch.class));
    }

    /** Shows the export screen afresh, the way a person arrives from another screen. */
    private void showExport() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.EXPORT));
    }

    private void publishOutcome(final RunState state, final JobReport report) {
        mirror().publishOutcome(state, report, null);
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {});
    }

    private RecordingFileRevealer revealer() {
        return (RecordingFileRevealer) injector.getInstance(FileRevealer.class);
    }

    private void assertEmptyState() {
        assertThat(isShown("export-empty")).isTrue();
        assertThat(optional("export-reveal")).isNull();
        assertThat(optional("export-accepted")).isNull();
        assertThat(optional("export-flagged")).isNull();
        assertThat(optional("export-path")).isNull();
    }

    private List<String> idsUnder(final Node root) {
        return Stream.concat(
                        Stream.ofNullable(root.getId()),
                        root instanceof Parent parent
                                ? parent.getChildrenUnmodifiable().stream().flatMap(child -> idsUnder(child).stream())
                                : Stream.empty())
                .toList();
    }

    // IF a completed run's file were not reported, THEN a person could not tell where the book went or how much of it
    // was accepted.
    @Test
    void screen_completedRun_rendersPathFormatAndBothCounts() {
        showExport();

        publishOutcome(RunState.COMPLETED, epubReport());

        assertThat(textOf("export-path")).contains("/books/Frankenstein.uk.epub");
        assertThat(textsUnder(required("export-format"))).contains("EPUB");
        assertThat(textsUnder(required("export-accepted"))).contains("1,237");
        assertThat(textsUnder(required("export-flagged"))).contains("3");
        assertThat(isShown("export-empty")).isFalse();
    }

    // IF a completed run offered no way to reveal its file, THEN the person would have to hunt for it by hand.
    @Test
    void screen_completedRun_offersToRevealTheFile() {
        showExport();

        publishOutcome(RunState.COMPLETED, epubReport());

        assertThat(isShown("export-reveal")).isTrue();
        assertThat(button("export-reveal").isDisabled()).isFalse();
    }

    // IF the screen rendered only what was published while it was open, THEN arriving after the run finished would
    // show an empty screen over a finished book.
    @Test
    void screen_reportPublishedBeforeOpening_rendersItOnArrival() {
        publishOutcome(RunState.COMPLETED, epubReport());

        showExport();

        assertThat(textOf("export-path")).contains("/books/Frankenstein.uk.epub");
        assertThat(textsUnder(required("export-accepted"))).contains("1,237");
        assertThat(isShown("export-reveal")).isTrue();
    }

    // IF the empty state offered a reveal control, THEN pressing it would point at a file that was never written.
    @Test
    void screen_noRunYet_showsEmptyStateAndOffersNothingToReveal() {
        showExport();

        assertEmptyState();
    }

    // IF a run that wrote nothing were reported as a finished book, THEN the screen would show a path to a file that
    // does not exist.
    @ParameterizedTest
    @MethodSource("runsThatWroteNothing")
    void screen_runThatWroteNothing_showsEmptyState(final JobReport report, final RunState state) {
        showExport();

        publishOutcome(state, report);

        assertEmptyState();
    }

    // IF a new run left the previous run's file on the screen, THEN the screen would report a book the new run has
    // not yet written.
    @Test
    void screen_newRunStarted_returnsToEmptyState() {
        showExport();
        publishOutcome(RunState.COMPLETED, epubReport());
        assertThat(isShown("export-reveal")).isTrue();

        mirror().publishRunStarted();
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {});

        assertEmptyState();
    }

    // IF the auxiliary offerings were missing or usable, THEN the screen would promise work this change does not do.
    @ParameterizedTest
    @MethodSource("auxiliaryControls")
    void screen_noRunYet_auxControlsPresentAndDisabled(final String id, final Class<? extends Node> type) {
        showExport();

        assertThat(required(id)).isInstanceOf(type);
        assertThat(required(id).isDisabled()).isTrue();
    }

    // IF a finished run changed the auxiliary offerings, THEN they would look available exactly when a person is
    // looking for what to do next.
    @ParameterizedTest
    @MethodSource("auxiliaryControls")
    void screen_completedRun_auxControlsPresentAndDisabled(final String id, final Class<? extends Node> type) {
        showExport();
        publishOutcome(RunState.COMPLETED, epubReport());

        assertThat(required(id)).isInstanceOf(type);
        assertThat(required(id).isDisabled()).isTrue();
    }

    // IF the screen carried a save-path field, THEN there would be a second, unproven way to choose where the book
    // goes, after the run had already written it.
    @Test
    void screen_completedRun_hasNoSavePathField() {
        showExport();
        publishOutcome(RunState.COMPLETED, epubReport());

        assertThat(required("export-screen").lookupAll(".text-input")).isEmpty();
        assertThat(idsUnder(required("export-screen"))).noneMatch(id -> id.contains("save") || id.contains("browse"));
    }

    // IF the screen carried a control that writes a book, THEN it would be a second write path beside the run's.
    @Test
    void screen_completedRun_theOnlyButtonIsReveal() {
        showExport();
        publishOutcome(RunState.COMPLETED, epubReport());

        assertThat(required("export-screen").lookupAll(".button").stream().map(Node::getId))
                .containsExactly("export-reveal");
    }

    // IF the reveal control were not wired, THEN pressing it would do nothing and the folder would never open.
    @Test
    void revealButton_pressed_callsRevealerWithTheWrittenPath() {
        showExport();
        publishOutcome(RunState.COMPLETED, epubReport());

        onFx(() -> button("export-reveal").fire());

        assertThat(revealer().revealed()).containsExactly(Path.of("/books/Frankenstein.uk.epub"));
    }

    // IF the empty screen claimed a book was ready, THEN a person who has not finished a run would be told a file
    // exists that was never written.
    @Test
    void screen_noRunYet_headingIsNeutralAndNoBookIsClaimed() {
        showExport();

        assertThat(labelText("export-title")).isEqualTo("Export");
        assertThat(optional("export-subtitle")).isNull();
    }

    // IF a completed run kept the neutral heading, THEN the screen would not announce that the book is ready.
    @Test
    void screen_completedRun_headingAnnouncesTheBookAndSubtitleExplainsIt() {
        showExport();

        publishOutcome(RunState.COMPLETED, epubReport());

        assertThat(labelText("export-title")).isEqualTo("Translated book ready");
        assertThat(textOf("export-subtitle")).startsWith("The run wrote your translated book");
    }

    // IF a new run left the ready heading up, THEN the screen would claim a book the new run has not written.
    @Test
    void screen_newRunStarted_headingReturnsToNeutral() {
        showExport();
        publishOutcome(RunState.COMPLETED, epubReport());

        mirror().publishRunStarted();
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {});

        assertThat(labelText("export-title")).isEqualTo("Export");
        assertThat(optional("export-subtitle")).isNull();
    }

    // IF the screen's labels were not from the active catalogue, THEN a Ukrainian session would read English.
    @ParameterizedTest
    @CsvSource({
        "en, Translated book ready, Written to, Open folder",
        "uk, Перекладена книга готова, Збережено в, Відкрити теку",
    })
    void screen_completedRun_rendersThatLanguagesLabels(
            final String language, final String title, final String written, final String reveal) {
        useLocale(Locale.forLanguageTag(language));
        showExport();

        publishOutcome(RunState.COMPLETED, epubReport());

        assertThat(labelText("export-title")).isEqualTo(title);
        assertThat(textsUnder(required("export-path"))).contains(written);
        assertThat(button("export-reveal").getText()).isEqualTo(reveal);
    }
}
