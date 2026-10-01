package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.StateMirror;

/**
 * The translation workspace, driven from opening a book to the end of its run through the real injector, once per
 * review mode.
 *
 * <p>Every screen has its own test against fakes; this is the one test that proves the mode chosen at launch changes
 * what the window does. The viewmodels, the runner, the document port, the translation engine and the model factory
 * are the production ones, over a generated book in a temporary directory; the pseudo model answers each paragraph,
 * and each directed fix, with the paragraph in capitals. The single substitute is the provider registry (see
 * {@link WorkspaceTestBase}).
 *
 * <p>Infrastructure: it proves an integration, not a product requirement.
 */
class TranslationWorkspaceEndToEndTest extends WorkspaceTestBase {

    // Each paragraph is at least 20 code points, so its capitals fail the echo and script checks outright and it is
    // flagged in every mode.
    private static final String SOURCE_TEXT =
            "It was a dark night.\n\nThe rain fell on the old house.\n\nNobody came to the door.\n";
    private static final String TRANSLATED_TEXT =
            "IT WAS A DARK NIGHT.\n\nTHE RAIN FELL ON THE OLD HOUSE.\n\nNOBODY CAME TO THE DOOR.\n";
    private static final List<String> LOCATORS = List.of("ch1 · p01", "ch1 · p02", "ch1 · p03");
    private static final List<String> CAPITALS =
            List.of("IT WAS A DARK NIGHT.", "THE RAIN FELL ON THE OLD HOUSE.", "NOBODY CAME TO THE DOOR.");
    private static final int SEGMENT_COUNT = 3;

    // IF the mode chosen at launch did not reach the engine and the window, THEN every case would run Unattended:
    // Assisted and Manual would never pause, and the three flagged paragraphs would end unreviewed.
    @ParameterizedTest
    @EnumSource(ReviewMode.class)
    void run_threeFlaggedParagraphs_pausesAsTheReviewModeSays(final ReviewMode mode) throws Exception {
        final Path book = Files.writeString(booksDir.resolve("Letter.txt"), SOURCE_TEXT, StandardCharsets.UTF_8);
        // The name the Export screen proposes beside the book for the chosen target, so a brief that is ignored cannot
        // pass.
        final Path proposed = booksDir.resolve("Letter.uk.txt");
        startWorkspace(mode);

        openBook(book, SEGMENT_COUNT);
        chooseBrief("uk", proposed);
        chooseModel();
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        startRun();
        final List<String> paused = acceptEachPause(mode == ReviewMode.UNATTENDED ? 0 : SEGMENT_COUNT);
        awaitCompletion();

        assertThat(paused).hasSize(mode == ReviewMode.UNATTENDED ? 0 : SEGMENT_COUNT);
        assertPauseLog(mode);
        assertRecords(mode);
        assertThat(proposed)
                .as("a finished run writes no book; only Export book does")
                .doesNotExist();
        exportBook(proposed);
        assertThat(Files.readString(proposed, StandardCharsets.UTF_8)).isEqualTo(TRANSLATED_TEXT);
        assertReopensWithSegments(proposed, SEGMENT_COUNT);
    }

    /** Accepts each pause as the person would, checking the panel opened on the segment the pause names. */
    private List<String> acceptEachPause(final int expected) throws Exception {
        final List<String> segmentIds = new ArrayList<>();
        final ReviewViewModel review = injector.getInstance(ReviewViewModel.class);
        for (int index = 0; index < expected; index++) {
            final String segmentId = pausedSegment(index == 0 ? null : segmentIds.get(index - 1));
            final SegmentView selected = onFx(() -> review.selected().get());
            assertThat(Objects.requireNonNull(selected).locator()).isEqualTo(LOCATORS.get(index));
            segmentIds.add(segmentId);
            onFx(() -> review.accept());
        }
        return segmentIds;
    }

    /** Waits for a review pause on a segment other than {@code previous} and for the panel to have it selected. */
    private String pausedSegment(final @Nullable String previous) throws Exception {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        final ReviewViewModel review = injector.getInstance(ReviewViewModel.class);
        waitUntil(() -> {
            final String paused = mirror.review().reviewPauseSegment().get();
            final SegmentView selected = review.selected().get();
            return paused != null
                    && !paused.equals(previous)
                    && selected != null
                    && selected.segmentId().equals(paused)
                    && review.acceptAvailable().get();
        });
        return onFx(() -> mirror.review().reviewPauseSegment().get());
    }

    /** Every pause is on-flagged, in Manual too: a flagged segment wins over after-segment. */
    private void assertPauseLog(final ReviewMode mode) {
        final List<String> messages = pauseMessages();
        assertThat(messages).hasSize(mode == ReviewMode.UNATTENDED ? 0 : SEGMENT_COUNT);
        assertThat(messages).allSatisfy(message -> assertThat(message).contains("reason=ON_FLAGGED"));
        assertThat(onFx(() ->
                        injector.getInstance(ReviewViewModel.class).selected().get()))
                .as("the panel opens only on a pause")
                .matches(selected -> (selected != null) == (mode != ReviewMode.UNATTENDED));
    }

    private void assertRecords(final ReviewMode mode) {
        final boolean reviewed = mode != ReviewMode.UNATTENDED;
        final String projectId = Objects.requireNonNull(onFx(
                        () -> injector.getInstance(CurrentProject.class).book().get()))
                .projectId();
        final ReviewDesk desk = injector.getInstance(ReviewDesk.class);
        final List<SegmentView> views = Objects.requireNonNull(
                desk.queue(projectId, ReviewFilter.ALL_SEGMENTS).data());
        assertThat(views).extracting(SegmentView::locator).containsExactlyElementsOf(LOCATORS);
        assertThat(views).extracting(SegmentView::maskedMachineTarget).containsExactlyElementsOf(CAPITALS);
        assertThat(views)
                .extracting(SegmentView::status)
                .containsOnly(reviewed ? SegmentStatus.ACCEPTED : SegmentStatus.FLAGGED);
        final Result<ReviewCounts> counts = desk.counts(projectId);
        assertThat(counts.data())
                .isEqualTo(new ReviewCounts(
                        SEGMENT_COUNT, 0, 0, reviewed ? 0 : SEGMENT_COUNT, reviewed ? SEGMENT_COUNT : 0, 0, 0, 0, 0));
        assertMirror();
    }

    private void assertMirror() {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        assertThat(onFx(() -> mirror.total().get())).isEqualTo(SEGMENT_COUNT);
        assertThat(onFx(() -> mirror.remaining().get())).isEqualTo(0);
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(0);
        assertThat(onFx(() -> mirror.flagged().get())).isEqualTo(SEGMENT_COUNT);
    }
}
