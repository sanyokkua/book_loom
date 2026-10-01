package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** A model that sends each request once reports it as one attempt, and a failed one as that attempt's failure. */
class ChatModelAttemptsTest {

    private final List<String> heard = new ArrayList<>();
    private final CallAttemptListener listener = new CallAttemptListener() {
        @Override
        public void started(final CallAttempt attempt) {
            heard.add(
                    "started " + attempt.number() + "/" + attempt.maxAttempts() + " cap " + attempt.maxOutputTokens());
        }

        @Override
        public void failed(final CallAttempt attempt, final ErrorCode code) {
            heard.add("failed " + attempt.number() + " " + code);
        }
    };

    @Test
    void chatWithListener_answeredModel_reportsOneStartedAttempt() {
        final ChatModel model = request -> Result.ok(new ChatResponse("ok", FinishReason.STOP, null));

        model.chat(new ChatRequest(List.of(), null, null, null, null, null, 64), listener);

        assertThat(heard).containsExactly("started 1/1 cap 64");
    }

    @Test
    void chatWithListener_failedModel_reportsTheFailure() {
        final ChatModel model =
                request -> Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing listens."));

        model.chat(new ChatRequest(List.of()), listener);

        assertThat(heard).containsExactly("started 1/1 cap null", "failed 1 unreachable");
    }
}
