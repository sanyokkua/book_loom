package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRow.Stage;

/**
 * The Detailed detail of a row: the prompt it was sent with and the reply, or what is known while the reply has not
 * come. The call is the one the row records (the call id on the row).
 */
class LiveCallPanelInputTest extends LiveCardTestBase {

    private static final String CALL = "translating-live-call";

    private static CallSnapshot waiting() {
        return LiveCallFixtures.waiting(1, 2, "She had lost her mother.", LiveCallFixtures.smallPrompt());
    }

    private void showDetailed(final CallSnapshot call, final Stage stage) {
        final LiveRow row =
                new LiveRow("s-1-1", "ch7 · p101", "She had lost her mother.", null, stage, null, List.of(), 1L);
        showLive(LiveRowFixtures.calls(call, List.of(row)));
        chooseMode(true);
    }

    private String bodyText(final String section) {
        return ThemeTestSupport.onFx(() -> ((TextArea) required(section + "-body")).getText());
    }

    // IF a waiting call left the reply's place empty, THEN the person could not tell a slow model from a lost call.
    @Test
    void waitingCall_saysItWaitsForTheReply() {
        showDetailed(waiting(), Stage.IN_CALL);

        assertThat(labelText(CALL + "-reply-note")).isEqualTo("Waiting for the reply...");
    }

    @Test
    void answeredCall_hasNoReplyNoteButTheReply() {
        showDetailed(waiting().answered("{\"items\":[]}", null, Duration.ofSeconds(1)), Stage.ACCEPTED);

        assertThat(isShown(CALL + "-reply-note")).isFalse();
        onFx(() -> ((TitledPane) required(CALL + "-reply")).setExpanded(true));
        assertThat(bodyText(CALL + "-reply-raw")).isEqualTo("{\"items\":[]}");
    }

    @ParameterizedTest
    @CsvSource({"FAILED", "CANCELLED"})
    void callEndedWithoutReply_saysNoReplyCameBack(final CallState end) {
        showDetailed(waiting().ended(end, Duration.ofSeconds(3)), Stage.IN_CALL);

        assertThat(labelText(CALL + "-reply-note")).isEqualTo("No reply came back.");
    }

    // IF the prompt's parts came out in another order than they were sent, THEN the person could not trust the context.
    @Test
    void promptContext_opened_showsEveryPartInTheOrderItWasSent() {
        showDetailed(waiting(), Stage.IN_CALL);

        onFx(() -> ((TitledPane) required(CALL + "-prompt")).setExpanded(true));

        assertThat(ThemeTestSupport.onFx(() -> required(CALL + "-prompt").lookupAll(".titled-pane").stream()
                        .map(node -> ((TitledPane) node).getText())
                        .toList()))
                .containsSubsequence("Style:", "[Book so far]", "[Glossary]", "[Characters]", "[Previous pairs]");
        assertThat(((TitledPane) required(CALL + "-prompt")).getText()).isEqualTo("Prompt context · 5 parts");
    }

    // IF a closed section held its text, THEN every call would lay out a prompt nobody opened.
    @Test
    void promptContext_closed_holdsNoTextUntilOpened() {
        showDetailed(waiting().withSent(List.of(new ChatMessage(ChatRole.SYSTEM, "Rules"))), Stage.IN_CALL);

        assertThat(bodyText(CALL + "-prompt-sent")).isEmpty();
    }

    // IF the call carried one clock beside the call line and another inside, THEN a slow call would show two times.
    @Test
    void call_waiting_showsNoSecondClockInsideTheCallBlock() {
        showDetailed(waiting(), Stage.IN_CALL);

        assertThat(isShown(CALL + "-clock")).isFalse();
        assertThat(isShown(CALL + "-timeout")).isFalse();
    }
}
