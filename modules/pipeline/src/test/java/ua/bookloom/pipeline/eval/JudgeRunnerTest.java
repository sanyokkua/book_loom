package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.pipeline.eval.JudgeReply.Winner;
import ua.bookloom.pipeline.eval.JudgeRunner.PairVerdict;
import ua.bookloom.pipeline.eval.JudgeRunner.Setup;

class JudgeRunnerTest {

    private static final JudgeItem ITEM = new JudgeItem(
            "ch01.md:3",
            "en",
            "uk",
            "She did not know, and would not ask. Pell sat silent by the lamp.",
            List.of(new JudgeItem.GoldTerm("Pell", "Пе́лл")),
            List.of("The boats came in late.", "Nobody spoke of the nets."));
    private static final String FIRST = "Вона не знала й не питала б. Пе́лл мовчав біля лампи.";
    private static final String SECOND = "Вона не знала й не питала б. Пе́лл мовчав.";
    private static final Setup SETUP = Setup.of("gemma4:26b", "q4_K_M", "ollama 0.12.6");

    @TempDir
    private Path cache;

    /** Answers with recorded replies in order and keeps every request it was sent. */
    private static final class RecordedModel implements ChatModel {

        private final Deque<String> replies = new ArrayDeque<>();
        private final List<ChatRequest> requests = new ArrayList<>();

        RecordedModel(final String... files) {
            List.of(files).forEach(file -> replies.add(recorded(file)));
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            return Result.ok(
                    new ChatResponse(Objects.requireNonNull(replies.poll(), "no reply left"), FinishReason.STOP));
        }

        private static String recorded(final String name) {
            try (InputStream in = Objects.requireNonNull(
                    JudgeRunnerTest.class.getResourceAsStream("/eval/judge/recorded/" + name), name)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private JudgeRunner runner(final ChatModel model) {
        return new JudgeRunner(model, SETUP, JudgeRubric.load(), cache);
    }

    @Test
    void judgePair_orderSwappedAnswersDisagree_isATie() {
        // Both orders prefer whatever stood in place A: a position bias, not a preference.
        final RecordedModel model = new RecordedModel("pairwise-a.json", "pairwise-a.json");

        assertThat(runner(model).judgePair(ITEM, FIRST, SECOND)).isEqualTo(PairVerdict.TIE);
        assertThat(model.requests).hasSize(2);
    }

    @Test
    void judgePair_bothOrdersAgree_isAWin() {
        final RecordedModel model = new RecordedModel("pairwise-a.json", "pairwise-b.json");

        assertThat(runner(model).judgePair(ITEM, FIRST, SECOND)).isEqualTo(PairVerdict.FIRST);
    }

    @Test
    void judgePair_bothOrdersPreferTheSecond_isASecondWin() {
        final RecordedModel model = new RecordedModel("pairwise-b.json", "pairwise-a.json");

        assertThat(runner(model).judgePair(ITEM, FIRST, SECOND)).isEqualTo(PairVerdict.SECOND);
    }

    @Test
    void judgePair_oneOrderUnparsed_isUnparsed() {
        final RecordedModel model = new RecordedModel("pairwise-a.json", "empty.txt");

        assertThat(runner(model).judgePair(ITEM, FIRST, SECOND)).isEqualTo(PairVerdict.UNPARSED);
    }

    @Test
    void judgePairOrders_bothOrdersAnswerA_givesEachOrderInTheCallersNames() {
        // Place A both times: the forward answer names the first translation, the swapped one the second.
        final RecordedModel model = new RecordedModel("pairwise-a.json", "pairwise-a.json");

        final JudgeRunner.PairOrders orders = runner(model).judgePairOrders(ITEM, FIRST, SECOND);

        assertThat(orders).isEqualTo(new JudgeRunner.PairOrders(Winner.A, Winner.B));
    }

    @Test
    void judgePairOrders_bothOrdersAgreeOnTheSecond_givesBTwice() {
        final RecordedModel model = new RecordedModel("pairwise-b.json", "pairwise-a.json");

        assertThat(runner(model).judgePairOrders(ITEM, FIRST, SECOND))
                .isEqualTo(new JudgeRunner.PairOrders(Winner.B, Winner.B));
    }

    @Test
    void judgePair_identicalOutputs_makesNoCall() {
        final RecordedModel model = new RecordedModel();

        assertThat(runner(model).judgePair(ITEM, FIRST, FIRST)).isEqualTo(PairVerdict.IDENTICAL);
        assertThat(model.requests).isEmpty();
    }

    @Test
    void judgePair_secondRun_isServedFromTheCache() {
        final RecordedModel first = new RecordedModel("pairwise-a.json", "pairwise-b.json");
        runner(first).judgePair(ITEM, FIRST, SECOND);
        final RecordedModel second = new RecordedModel();

        assertThat(runner(second).judgePair(ITEM, FIRST, SECOND)).isEqualTo(PairVerdict.FIRST);
        assertThat(second.requests).isEmpty();
    }

    @Test
    void judgePairs_batch_countsJudgedAndCachedCases() {
        runner(new RecordedModel("pairwise-a.json", "pairwise-b.json")).judgePair(ITEM, FIRST, SECOND);
        final RecordedModel model = new RecordedModel("pairwise-b.json", "pairwise-a.json");
        final JudgeItem other = new JudgeItem("ch01.md:4", "en", "uk", "The 14 boats.", List.of(), List.of());

        final List<PairVerdict> verdicts = runner(model)
                .judgePairs(List.of(
                        new JudgeRunner.PairCase(ITEM, FIRST, SECOND),
                        new JudgeRunner.PairCase(other, "14 човнів.", "Чотирнадцять човнів."),
                        new JudgeRunner.PairCase(other, SECOND, SECOND)));

        assertThat(verdicts).containsExactly(PairVerdict.FIRST, PairVerdict.SECOND, PairVerdict.IDENTICAL);
        assertThat(model.requests).hasSize(2);
    }

    @Test
    void judgePair_request_showsTheCaseButNeverTheArmAndKeepsThinkingOff() {
        final RecordedModel model = new RecordedModel("pairwise-a.json", "pairwise-b.json");

        runner(model).judgePair(ITEM, FIRST, SECOND);

        final ChatRequest request = model.requests.getFirst();
        final String user = request.messages().getLast().content();
        assertThat(request.reasoningEnabled()).isEqualTo(ReasoningLevel.OFF);
        assertThat(request.messages().getFirst().content()).contains("labelled A and B");
        assertThat(user)
                .contains(ITEM.source(), "Pell → Пе́лл", "The boats came in late.", FIRST, SECOND, "en → uk")
                .doesNotContainIgnoringCase("baseline")
                .doesNotContainIgnoringCase("candidate");
        assertThat(model.requests.getLast().messages().getLast().content().indexOf(SECOND))
                .isLessThan(
                        model.requests.getLast().messages().getLast().content().indexOf(FIRST));
    }

    @Test
    void judgeOne_recordedReply_isScored() {
        final RecordedModel model = new RecordedModel("pointwise-inside-think.txt");

        final JudgeReply reply = runner(model).judgeOne(ITEM, FIRST);

        assertThat(reply.describe())
                .isEqualTo("scored meaning=5 grammar=5 terminology=5 fluency=5 errors=0 mqm=0 verdict=good");
        assertThat(model.requests.getFirst().messages().getFirst().content()).contains("\"scores\"");
    }

    @Test
    void block_gemmaTranslator_isSameFamily() {
        final JudgeRunner.Block block = runner(new RecordedModel()).block("gemma4:e4b-mlx");

        assertThat(block.sameFamily()).isTrue();
        assertThat(block.rubricVersion()).isEqualTo("bl-judge-1");
        assertThat(block.reasoning()).isEqualTo("off");
        assertThat(block.mode()).isEqualTo("pairwise");
    }

    @Test
    void block_otherTranslator_isNotSameFamily() {
        assertThat(runner(new RecordedModel()).block("qwen3:8b").sameFamily()).isFalse();
    }
}
