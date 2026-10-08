package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.eval.ReplayConfig.Mode;

/** Replay against scripted models: the log's own replies are answered back, so the scores can be checked by hand. */
class ReplayRunnerTest {

    private static LogReplayCorpus corpus;

    @BeforeAll
    static void load() throws URISyntaxException {
        corpus = LogReplayCorpus.load(
                Path.of(ReplayRunnerTest.class
                        .getResource("/eval/replay/sample-trace.txt")
                        .toURI()),
                0);
    }

    private static ReplayConfig config(final Mode mode, final int limit, final int target) {
        return new ReplayConfig(Path.of("unused"), mode, 0, limit, target);
    }

    /** A model that answers a logged request with the reply the log holds for it, and records what it was sent. */
    private static ChatModel loggedReplies(final List<ChatRequest> sent) {
        final Map<String, String> byUser = new java.util.HashMap<>();
        corpus.calls()
                .forEach(call -> call.reply()
                        .ifPresent(reply ->
                                byUser.put(call.chatRequest().messages().get(1).content(), reply)));
        return request -> {
            sent.add(request);
            final String reply = byUser.get(request.messages().get(1).content());
            return reply == null
                    ? Result.err(AppError.of(ErrorCode.internal, "none", "no logged reply"))
                    : Result.ok(new ChatResponse(reply, FinishReason.STOP));
        };
    }

    private static StageRow row(final List<StageRow> rows, final int order) {
        return rows.stream()
                .filter(r -> r.id().contains(String.format("-%03d-", order)))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void run_raw_sendsEveryLoggedRequestAsItWentOut() {
        final List<ChatRequest> sent = new ArrayList<>();

        final List<StageRow> rows = new ReplayRunner(loggedReplies(sent)).run(corpus, config(Mode.RAW, 0, 40));

        assertThat(rows).hasSize(corpus.calls().size());
        assertThat(sent.stream().map(ChatRequest::messages).toList())
                .isEqualTo(corpus.calls().stream()
                        .map(call -> call.chatRequest().messages())
                        .toList());
    }

    @Test
    void run_raw_scoresTheNewReplyByTheRunsOwnJudges() {
        final List<StageRow> rows =
                new ReplayRunner(loggedReplies(new ArrayList<>())).run(corpus, config(Mode.RAW, 0, 40));

        assertThat(row(rows, 0).passed()).as(row(rows, 0).detail()).isTrue();
        assertThat(row(rows, 0).detail()).contains("single accepted", "logged reply ok");
        assertThat(row(rows, 4).passed()).as("closer residue is refused").isFalse();
        assertThat(row(rows, 3).passed())
                .as("an item left in English is refused")
                .isFalse();
        assertThat(row(rows, 3).refused()).isEqualTo(1);
        assertThat(row(rows, 7).detail()).contains("structure only");
    }

    @Test
    void run_raw_aCallTheModelFails_countsAsFailedNotScored() {
        final ChatModel failing =
                request -> Result.err(AppError.of(ErrorCode.internal, "down", "the endpoint is down"));

        final List<StageRow> rows = new ReplayRunner(failing).run(corpus, config(Mode.RAW, 2, 40));

        assertThat(rows).hasSize(2).allSatisfy(r -> {
            assertThat(r.passed()).isFalse();
            assertThat(r.failed()).isEqualTo(1);
        });
    }

    @Test
    void run_fast_sendsTheSelectionRebuiltByTheCurrentPrompts() {
        final List<ChatRequest> sent = new ArrayList<>();
        final ChatModel echo = request -> {
            sent.add(request);
            return Result.ok(new ChatResponse("{\"target\":\"x\"}", FinishReason.STOP));
        };

        final List<StageRow> rows = new ReplayRunner(echo).run(corpus, config(Mode.FAST, 0, 4));

        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(r -> assertThat(r.id()).startsWith("fast-"));
        assertThat(sent).hasSize(4);
        final List<ChatRequest> logged =
                corpus.calls().stream().map(LoggedCall::chatRequest).toList();
        assertThat(sent)
                .allSatisfy(request ->
                        assertThat(logged.stream().map(ChatRequest::messages)).doesNotContain(request.messages()));
        assertThat(sent.stream().map(request -> request.messages().get(1).content()))
                .anySatisfy(user -> assertThat(user).contains("The lamp went out again."));
    }

    @Test
    void run_fast_reportCarriesNoLogText() {
        final ChatModel echo = request -> Result.ok(new ChatResponse("{\"target\":\"x\"}", FinishReason.STOP));

        final StageReport report =
                new StageReport("replay", "m", new ReplayRunner(echo).run(corpus, config(Mode.FAST, 0, 6)));

        assertThat(report.table() + report.json())
                .doesNotContain("lamp", "Лампа", "harbour", "ferry", "Nobody", "Двері");
    }
}
