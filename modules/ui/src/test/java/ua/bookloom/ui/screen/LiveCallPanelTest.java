package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SegmentLive;

/** The live panel on the running dashboard: the two model calls as the mirror publishes them. */
class LiveCallPanelTest extends TranslatingScreenTestBase {

    private static final String CARD = "translating-live-card";
    private static final String CURRENT = CARD + "-current";
    private static final String PREVIOUS = CARD + "-previous";
    private static final String SOURCE = "She had lost her mother, and the poor girl wept.";

    private CallSnapshot waiting(final long id, final int segments) {
        return LiveCallFixtures.waiting(id, segments, SOURCE, LiveCallFixtures.smallPrompt());
    }

    private void showCalls(final CallSnapshot current, final @Nullable CallSnapshot previous) {
        publishCalls(LiveCallFixtures.calls(current, previous));
    }

    private void publishCalls(final LiveCalls calls) {
        showTranslating();
        onFx(() -> injector.getInstance(BookBriefViewModel.class).setSourceLanguage("en"));
        mirror().live().publishCalls(calls);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the panel sent fewer sources than the call carried, THEN the person would not see what was really sent.
    @Test
    void waitingBatchOfEight_showsEverySourceAndTheWaitingHeader() {
        showCalls(waiting(2, 8), null);

        assertThat(labelText(CURRENT + "-caption")).isEqualTo("8 segments sent to the model");
        assertThat(labelText(CURRENT + "-segment-1-source-text")).isEqualTo(SOURCE + " #1");
        assertThat(labelText(CURRENT + "-segment-8-source-text")).isEqualTo(SOURCE + " #8");
        assertThat(labelText(CURRENT + "-segment-8-locator")).isEqualTo("ch7 · p208");
        assertThat(labelText(CURRENT + "-state")).isEqualTo("Waiting for the model");
        assertThat(labelText(CURRENT + "-attempt")).isEqualTo("attempt 1 of 2");
        assertThat(labelText(CURRENT + "-clock")).isEqualTo("waiting 0:12");
        assertThat(labelText(CURRENT + "-timeout")).isEqualTo("timeout 3:00");
        assertThat(labelText(CURRENT + "-kind")).isEqualTo("Translate");
        assertThat(labelText(CURRENT + "-segment-1-target-text")).isEqualTo("waiting for the model…");
        assertThat(isShown(CURRENT + "-reply")).isFalse();
        assertThat(isShown(PREVIOUS)).isFalse();
    }

    // IF the reply were not shown once the call is answered, THEN the person could not check what came back.
    @Test
    void answeredCall_showsTheReplyTheClockAndTheTokens() {
        final CallSnapshot answered = waiting(1, 2).answered("{\"items\":[]}", usage(412, 96), Duration.ofSeconds(7));

        showCalls(answered, null);

        assertThat(labelText(CURRENT + "-state")).isEqualTo("Answered");
        assertThat(labelText(CURRENT + "-clock")).isEqualTo("took 0:07");
        assertThat(labelText(CURRENT + "-tokens")).isEqualTo("tokens: 412 in · 96 out");
        assertThat(isShown(CURRENT + "-reply")).isTrue();
        assertThat(replyText()).isEqualTo("{\"items\":[]}");
    }

    private String replyText() {
        return textsUnder(required(CURRENT + "-reply-body")).get(0);
    }

    private static TokenUsage usage(final int prompt, final int completion) {
        return new TokenUsage(prompt, completion, Duration.ofSeconds(5));
    }

    // IF the prompt parts came out in another order than they were sent, THEN the person could not trust the context.
    @Test
    void promptContext_showsEveryPartInTheOrderItWasSent() {
        showCalls(waiting(1, 1), null);

        assertThat(labelText(CURRENT + "-prompt-part-1-head")).isEqualTo("Style:");
        assertThat(labelText(CURRENT + "-prompt-part-2-head")).isEqualTo("[Book so far]");
        assertThat(labelText(CURRENT + "-prompt-part-3-head")).isEqualTo("[Glossary]");
        assertThat(labelText(CURRENT + "-prompt-part-4-head")).isEqualTo("[Characters]");
        assertThat(labelText(CURRENT + "-prompt-part-5-head")).isEqualTo("[Previous pairs]");
        assertThat(labelText(CURRENT + "-prompt-part-5-text")).isEqualTo("Rain.\nДощ.");
        assertThat(textsUnder(required(CURRENT + "-prompt-body")).stream()
                        .distinct()
                        .toList()
                        .subList(0, 3))
                .containsExactly("System message", "Style:", "Literary, warm.");
        assertThat(((TitledPane) required(CURRENT + "-prompt")).getText()).isEqualTo("Prompt context · 5 parts");
    }

    // IF a new call replaced the old one outright, THEN what the person was reading would vanish.
    @Test
    void newCall_movesTheOldOneToThePreviousBlock() {
        showCalls(waiting(2, 3), LiveCallFixtures.answered(waiting(1, 2), "{}"));

        assertThat(labelText(CURRENT + "-title")).isEqualTo("Current call");
        assertThat(labelText(PREVIOUS + "-title")).isEqualTo("Previous call");
        assertThat(labelText(CURRENT + "-caption")).isEqualTo("3 segments sent to the model");
        assertThat(labelText(PREVIOUS + "-caption")).isEqualTo("2 segments sent to the model");
        assertThat(labelText(PREVIOUS + "-state")).isEqualTo("Answered");
        assertThat(isShown(PREVIOUS)).isTrue();
    }

    // IF the first block still said "Current" after the run ended, THEN it would claim a call is in flight.
    @ParameterizedTest
    @CsvSource({"COMPLETED", "FAILED", "STOPPED"})
    void runEnded_firstBlockIsRelabelledLastCall(final RunState ended) {
        showCalls(LiveCallFixtures.answered(waiting(2, 1), "{}"), LiveCallFixtures.answered(waiting(1, 1), "{}"));

        publish(ended);

        assertThat(labelText(CURRENT + "-title")).isEqualTo("Last call");
        assertThat(labelText(PREVIOUS + "-title")).isEqualTo("Previous call");
    }

    // IF the card were empty before the first call, THEN the person would see a blank box and wonder if it broke.
    @Test
    void beforeAnyCall_saysNothingWasSentAndShowsNoBlocks() {
        showTranslating();
        publish(RunState.RUNNING);

        assertThat(isShown(CARD + "-none")).isTrue();
        assertThat(labelText(CARD + "-none")).startsWith("No model call yet");
        assertThat(isShown(CURRENT)).isFalse();
        assertThat(isShown(PREVIOUS)).isFalse();
    }

    // IF the outcome chips were not worded from the notes, THEN a fallback or a flag would pass unseen.
    @Test
    void outcomeNotes_showAsChipsOnTheirSegments() {
        final CallSnapshot call = LiveCallFixtures.answered(waiting(1, 3), "{}")
                .withOutcome(new SegmentOutcomeNote("s-1-1", SegmentOutcomeNote.Kind.ADOPTED, ""))
                .withOutcome(new SegmentOutcomeNote("s-1-2", SegmentOutcomeNote.Kind.FELL_BACK, "MISSING"))
                .withOutcome(new SegmentOutcomeNote("s-1-3", SegmentOutcomeNote.Kind.FLAGGED, "meaning"));

        showCalls(call, null);

        assertThat(chipTexts(CURRENT + "-segment-1-chips")).containsExactly("Adopted");
        assertThat(chipTexts(CURRENT + "-segment-2-chips")).containsExactly("Fell back: missing from the reply");
        assertThat(chipTexts(CURRENT + "-segment-3-chips")).containsExactly("Flagged: meaning changed");
    }

    private List<String> chipTexts(final String id) {
        return ThemeTestSupport.onFx(() -> ((FlowPane) required(id))
                .getChildren().stream().map(node -> ((Label) node).getText()).toList());
    }

    // IF a segment reused from memory showed "No translation", THEN a correct reuse would look like a gap.
    @Test
    void decidedSegment_showsItsTargetScoreAndPath_notAnEmptyTranslation() {
        final CallSnapshot call = LiveCallFixtures.answered(waiting(1, 2), "{}");
        publishCalls(LiveCallFixtures.calls(
                call,
                null,
                Map.of(
                        "s-1-1", new SegmentLive("Дощ лив стіною.", 0.93, SegmentPath.DRAFT),
                        "s-1-2", new SegmentLive("Той самий рядок.", null, SegmentPath.TM_REUSE))));

        assertThat(labelText(CURRENT + "-segment-1-target-text")).isEqualTo("Дощ лив стіною.");
        assertThat(chipTexts(CURRENT + "-segment-1-chips")).containsExactly("judge 0.93", "auto-accepted");
        assertThat(labelText(CURRENT + "-segment-2-target-text")).isEqualTo("Той самий рядок.");
        assertThat(chipTexts(CURRENT + "-segment-2-chips")).containsExactly("from memory");
    }

    // IF a failed or cancelled call looked like an answered one, THEN the person would trust a reply that never came.
    @ParameterizedTest
    @CsvSource({"FAILED, Failed", "CANCELLED, Cancelled"})
    void endedWithoutReply_showsTheStateAndNoReply(final CallState end, final String text) {
        showCalls(waiting(1, 1).ended(end, Duration.ofSeconds(30)), null);

        assertThat(labelText(CURRENT + "-state")).isEqualTo(text);
        assertThat(isShown(CURRENT + "-reply")).isFalse();
        assertThat(labelText(CURRENT + "-segment-1-target-text")).isEqualTo("No translation for this segment.");
    }

    // IF the clock were fixed at the first value, THEN a stuck call would look the same as a fresh one.
    @Test
    void waitingCall_clockFollowsThePublishedTime() {
        showCalls(waiting(1, 1), null);

        mirror().live()
                .publishCalls(new LiveCalls(waiting(1, 1), null, Map.of(), LiveCallFixtures.STARTED.plusSeconds(75)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText(CURRENT + "-clock")).isEqualTo("waiting 1:15");
    }
}
