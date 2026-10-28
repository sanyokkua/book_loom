package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;

/** K repeats run with the seeds 1000 upwards, and each repeat leaves its run-level aggregate. */
class EvalRepeatsTest {

    private static final ChatRequest UNSEEDED = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate.")));

    @Test
    void run_threeRepeats_usesSeeds1000To1002AndKeepsThreeAggregates() {
        final List<Integer> sent = new CopyOnWriteArrayList<>();
        final ChatModel model = request -> {
            sent.add(Objects.requireNonNull(request.seed()));
            return Result.ok(new ChatResponse("ok", FinishReason.STOP));
        };

        final EvalRepeats.Runs<Integer> runs = new EvalRepeats(3)
                .run(
                        model,
                        seeded -> {
                            seeded.chat(UNSEEDED);
                            return sent.size();
                        },
                        calls -> calls / 10.0);

        assertThat(sent).containsExactly(1000, 1001, 1002);
        assertThat(runs.seeds()).containsExactly(1000, 1001, 1002);
        assertThat(runs.aggregates()).containsExactly(0.1, 0.2, 0.3);
    }

    @Test
    void fromEnvironment_unset_isOneRepeat() {
        assertThat(EvalRepeats.fromEnvironment(Map.of()).seeds()).containsExactly(1000);
    }

    @Test
    void fromEnvironment_three_isThreeRepeats() {
        assertThat(EvalRepeats.fromEnvironment(Map.of(EvalRepeats.ENV, "3")).k())
                .isEqualTo(3);
    }
}
