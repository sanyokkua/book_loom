package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.i18n.MessageKey;

/** What the progress dialog's "What it says" shows for a call that gave no targets. */
class ProgressStepDetailTest {

    private static final String RAW = "{\"genre\":{\"value\":\"Mystery\"}}";

    // IF the Simple view showed the raw reply, THEN a person would read the model's JSON instead of the decision.
    @Test
    void plainOutput_simpleViewWithNoTargets_tellsTheDecidedValuesNotTheRawReply() {
        assertThat(ProgressStepDetail.plainOutput(false, "", RAW, "Genre: Mystery"))
                .isEqualTo("Genre: Mystery");
    }

    @Test
    void plainOutput_simpleViewWithNothingDecided_isEmptyNotTheRawReply() {
        assertThat(ProgressStepDetail.plainOutput(false, "", RAW, "")).isNull();
    }

    @Test
    void plainOutput_detailedViewWithNoTargets_keepsTheRawReply() {
        assertThat(ProgressStepDetail.plainOutput(true, "", RAW, "Genre: Mystery"))
                .isEqualTo(RAW);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void plainOutput_targetsPresent_alwaysWinOverTheReply(final boolean technical) {
        assertThat(ProgressStepDetail.plainOutput(technical, "Привіт", RAW, "Genre: Mystery"))
                .isEqualTo("Привіт");
    }

    // IF a finished call with no translated text said "Nothing yet.", THEN a done reviewer would look as if it had not
    // answered; it says whether a reply came and where to read it.
    @ParameterizedTest
    @CsvSource({
        ", , PROGRESS_NOTHING",
        "WAITING, , PROGRESS_NOTHING",
        "ANSWERED, '{\"edits\":[]}', PROGRESS_REPLY_IN_DETAILED",
        "ANSWERED, '', LIVE_CALL_REPLY_NONE",
        "FAILED, , LIVE_CALL_REPLY_NONE",
        "CANCELLED, , LIVE_CALL_REPLY_NONE",
    })
    void nothingSaid_callStateAndReply_namesWhatTheEmptyAnswerMeans(
            final CallState state, final String reply, final MessageKey expected) {
        assertThat(ProgressStepDetail.nothingSaid(state, reply)).isEqualTo(expected);
    }
}
