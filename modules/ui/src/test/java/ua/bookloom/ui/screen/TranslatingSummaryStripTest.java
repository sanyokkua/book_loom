package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.Throughput;

/**
 * The summary strip on top of the Translating screen: the time left or the outcome in one line, the bar, the counts
 * and the one line of facts, on every row of the run-state table except that a book not yet started has no counts.
 */
class TranslatingSummaryStripTest extends TranslatingScreenTestBase {

    // 700 translated untouched, 68 fixed by the checks, 3 flagged, 20 kept as they are, 449 not decided yet.
    private static final JobProgress RUNNING =
            new JobProgress(JobStage.TRANSLATE, 7, 11, 788, 3, 449, 41, 66, 700, 68, 20);

    private void running(final JobProgress progress) {
        chooseModel();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.RUNNING);
        mirror().publishProgress(progress);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void publishThroughput(final long leftMinutes, final long elapsedMinutes) {
        mirror().live()
                .publishThroughput(new Throughput(
                        24.0, false, Duration.ofMinutes(leftMinutes), Duration.ofMinutes(elapsedMinutes)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void completeWithFlagged(final int flagged, final int doubted) {
        mirror().live()
                .publishFlaggedQueue(IntStream.range(0, flagged)
                        .mapToObj(i -> new FlaggedRow("s-" + i, "ch7 · p" + i, List.of(), null))
                        .toList());
        mirror().live().publishSuspicious(doubted);
        mirror().publishOutcome(RunState.COMPLETED, completedReport(), null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the counts mixed repaired with untouched segments, THEN the repair rate would be invisible.
    @Test
    void counts_700_68_3_20_readEachWithItsWord() {
        running(RUNNING);

        assertThat(labelText("translating-count-translated")).isEqualTo("700 translated");
        assertThat(labelText("translating-count-fixed")).isEqualTo("68 fixed automatically");
        assertThat(labelText("translating-count-to-review")).isEqualTo("3 to review");
        assertThat(labelText("translating-count-kept")).isEqualTo("20 kept as is");
    }

    // IF the line counted pending segments as done, THEN the person would think the book is further along.
    @Test
    void line_progressPublished_saysSegmentsDoneAndTheChapter() {
        running(RUNNING);

        assertThat(labelText("translating-strip-line")).isEqualTo("791 of 1,240 segments done · Chapter 7 of 11");
    }

    // IF a part of the line were shown while unknown, THEN it would say "elapsed" with nothing to count.
    @Test
    void line_elapsedKnown_endsWithTheElapsedTime() {
        running(RUNNING);

        publishThroughput(70, 81);

        assertThat(labelText("translating-strip-line")).endsWith(" · 1h 21m elapsed");
    }

    // IF an empty book divided by its total, THEN the strip would show NaN instead of zero.
    @Test
    void strip_noSegments_readsZeroOfZeroAndAnEmptyBar() {
        running(ua.bookloom.ui.ProgressFixtures.progress(7, 11, 0, 0, 0));

        assertThat(labelText("translating-strip-line")).startsWith("0 of 0 segments done");
        assertThat(progressBar().getProgress()).isZero();
    }

    // IF a reopened project showed 0 of 0 until a run began, THEN a finished or half-done book would look empty.
    @ParameterizedTest
    @CsvSource({"142, 134, 8 of 142 segments done, 0.05", "142, 0, 142 of 142 segments done, 1.0"})
    void strip_projectReopenedWithNoRun_showsTheStoredCounts(
            final int total, final int pending, final String line, final double fraction) throws Exception {
        desk.willAnswerCounts(new ReviewCounts(total, total - pending, 0, 0, 0, pending, 0, 0, 0));
        readyToStart();
        showTranslating();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-strip-line")).startsWith(line);
        assertThat(progressBar().getProgress()).isCloseTo(fraction, org.assertj.core.data.Offset.offset(0.01));
    }

    // IF the bar's tooltip did not give the steps, THEN 64 % beside 791 segments would look like a mistake.
    @Test
    void bar_hovered_givesTheStepsCountedIncludingChecks() {
        running(RUNNING);

        assertThat(TooltipProbe.tipText(progressBar()))
                .isEqualTo("64 % of all the work — 791 of 1,240 steps, checks included");
    }

    // IF a running strip did not lead with the time left, THEN the one thing a person asks first would be missing.
    @Test
    void bigLine_translatingWithATimeLeft_saysAboutHowLong() {
        running(RUNNING);

        publishThroughput(70, 81);

        assertThat(labelText("translating-strip-big")).isEqualTo("about 1 h 10 min left");
    }

    // IF a finished book still said "left", THEN it would promise work that is over.
    @Test
    void bigLine_completedRun_saysHowLongItTook() {
        running(RUNNING);
        publishThroughput(0, 145);

        completeWithFlagged(0, 0);

        assertThat(labelText("translating-strip-big")).isEqualTo("Finished in 2 h 25 min");
    }

    // IF a finished run's strip lost the doubts, THEN a silent leak in an accepted segment would pass unseen.
    @Test
    void completed_sixToReviewAndThreeDoubted_offersTheReviewButtonAndTheSecondLookCount() {
        running(RUNNING);

        completeWithFlagged(6, 3);

        assertThat(button("translating-strip-review").getText()).isEqualTo("6 to review");
        assertThat(labelText("translating-count-second-look")).isEqualTo("3 worth a second look");
        assertThat(TooltipProbe.tipText(required("translating-strip-review")))
                .isEqualTo("Opens the segments that need your review.");
    }

    // IF the button showed with nothing to review, THEN it would open an empty list.
    @Test
    void completed_nothingToReview_hasNoReviewButtonAndNoSecondLook() {
        running(RUNNING);

        completeWithFlagged(0, 0);

        assertThat(isShown("translating-strip-review")).isFalse();
        assertThat(isShown("translating-count-second-look")).isFalse();
    }

    // IF the strip's button did nothing, THEN "n to review" would be a label that looks like a control.
    @Test
    void reviewButton_pressed_opensTheReviewDesk() {
        running(RUNNING);
        completeWithFlagged(6, 3);

        onFx(() -> button("translating-strip-review").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("review-desk")).isTrue();
    }

    // IF the way the counts are made were left out, THEN "translated" against "fixed automatically" could not be read.
    @Test
    void howCounted_strip_isAClosedFoldThatOpens() {
        running(RUNNING);
        final TitledPane fold = (TitledPane) required("translating-how-counted");
        assertThat(fold.isExpanded()).isFalse();

        onFx(() -> fold.setExpanded(true));

        assertThat(textOf("translating-how-counted")).isNotBlank();
    }

    // IF the strip's counts truncated at the narrowest window, THEN "700 translated" would read "7…".
    @Test
    void counts_atMinimum_showNoTruncatedWords() {
        running(RUNNING);
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        completeWithFlagged(6, 3);

        assertThat(required("translating-strip").lookupAll(".label"))
                .filteredOn(node -> node.getId() != null && node.getId().startsWith("translating-count-"))
                .isNotEmpty()
                .allSatisfy(label ->
                        assertThat(((Label) label).getWidth()).isGreaterThanOrEqualTo(((Label) label).prefWidth(-1)));
    }

    // IF a demonstration control or a requirement badge of the design mockup reached the window, THEN the person
    // would see a button that is not part of the product.
    @Test
    void screen_running_carriesNoMockupScaffolding() {
        running(RUNNING);

        assertThat(textOf("translating-screen")).doesNotContain("Mockup", "req(", "Chapter 2 orbits");
        assertThat(idsStartingWith("translating-")).noneMatch(id -> id.contains("mockup") || id.contains("demo"));
    }
}
