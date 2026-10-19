package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.BookBriefViewModel;

/** What a call panel says of a call that sent no segment, and of a reply that has not come: input, summary, waiting. */
class LiveCallPanelInputTest extends TranslatingScreenTestBase {

    private static final String CURRENT = "translating-live-card-current";
    private static final String SOURCE = "She had lost her mother, and the poor girl wept.";

    private CallSnapshot waiting(final long id, final int segments) {
        return LiveCallFixtures.waiting(id, segments, SOURCE, LiveCallFixtures.smallPrompt());
    }

    private void showCalls(final CallSnapshot current) {
        showTranslating();
        onFx(() -> injector.getInstance(BookBriefViewModel.class).setSourceLanguage("en"));
        mirror().live().publishCalls(LiveCallFixtures.calls(current, null));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String bodyText(final String section) {
        return ThemeTestSupport.onFx(() -> ((TextArea) required(section + "-body")).getText());
    }

    private static CallSnapshot scanCall() {
        return LiveCallFixtures.waiting(1, 0, SOURCE, List.of())
                .withSent(List.of(
                        new ChatMessage(ChatRole.SYSTEM, "Rules"),
                        new ChatMessage(ChatRole.USER, "Which of these are names? Gale, Baker Street")));
    }

    // IF a call about no segment showed an empty segment box, THEN the person could not read what the model was asked.
    @Test
    void noSegmentCall_showsTheOpenInputFoldAndASummaryLineInsteadOfASegmentBox() {
        showCalls(scanCall());

        assertThat(isShown(CURRENT + "-segments")).isFalse();
        assertThat(isShown(CURRENT + "-input")).isTrue();
        assertThat(((TitledPane) required(CURRENT + "-input")).isExpanded()).isTrue();
        assertThat(((TitledPane) required(CURRENT + "-input")).getText()).isEqualTo("Input");
        assertThat(bodyText(CURRENT + "-input")).isEqualTo("Which of these are names? Gale, Baker Street");
        assertThat(labelText(CURRENT + "-caption")).isEqualTo("A call about no segment · 49 characters sent");
    }

    @Test
    void noSegmentCall_answered_summaryCountsTheReplyToo() {
        showCalls(scanCall().answered("{\"names\":[]}", null, Duration.ofSeconds(2)));

        assertThat(labelText(CURRENT + "-caption"))
                .isEqualTo("A call about no segment · 49 characters sent · 12 characters back");
    }

    // IF a call with segments grew an Input fold, THEN the same text would show twice beside its segment rows.
    @Test
    void callWithSegments_showsNoInputFoldAndKeepsItsSegmentBox() {
        showCalls(waiting(1, 2));

        assertThat(isShown(CURRENT + "-input")).isFalse();
        assertThat(isShown(CURRENT + "-segments")).isTrue();
    }

    // IF a waiting call left the reply's place empty, THEN the person could not tell a slow model from a lost call.
    @Test
    void waitingCall_saysItWaitsForTheReply() {
        showCalls(waiting(1, 1));

        assertThat(labelText(CURRENT + "-reply-note")).isEqualTo("Waiting for the reply\u2026");
    }

    @Test
    void answeredCall_hasNoReplyNote() {
        showCalls(waiting(1, 1).answered("{}", null, Duration.ofSeconds(1)));

        assertThat(isShown(CURRENT + "-reply-note")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"FAILED", "CANCELLED"})
    void callEndedWithoutReply_saysNoReplyCameBack(final CallState end) {
        showCalls(waiting(1, 1).ended(end, Duration.ofSeconds(3)));

        assertThat(labelText(CURRENT + "-reply-note")).isEqualTo("No reply came back.");
    }
}
