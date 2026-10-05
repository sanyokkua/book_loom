package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.batch.ItemOutcome;
import ua.bookloom.pipeline.batch.ItemStatus;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.eval.BatchEvalCases.Batch;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * Sends the batches of {@link BatchEvalCases} through the production batch prompt builder and reply parser
 * and measures every reply before any fallback a run would make.
 */
@Slf4j
final class BatchEvalRunner {

    private static final String SOURCE = PromptEvalCases.SOURCE_LANGUAGE;
    private static final String TARGET = PromptEvalCases.TARGET_LANGUAGE;

    private final ModelCalls calls;
    private final BatchPromptBuilder builder;
    private final BatchReplyParser parser = new BatchReplyParser(new ObjectMapper());

    BatchEvalRunner(final ModelCalls calls) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.builder = new BatchPromptBuilder(
                new PromptTemplates(),
                new CallFrame(SOURCE, TARGET, StyleSheet.from(BookBrief.defaults(SOURCE)), ForeignPassagePolicy.KEEP));
    }

    /** Runs every batch of every size in {@code sizes}. */
    List<BatchEvalRow> runAll(final List<Integer> sizes) {
        final List<BatchEvalRow> rows = new ArrayList<>();
        for (final int size : sizes) {
            for (final Batch batch : BatchEvalCases.batches(size)) {
                rows.add(run(batch));
            }
        }
        return rows;
    }

    BatchEvalRow run(final Batch batch) {
        log.info("Batch eval size={}", batch.items().size());
        final List<ChatMessage> messages = builder.messagesFor(batch.context(), batch.items());
        final ChatRequest request = builder.requestFor(messages, batch.items(), false);
        final Result<ChatResponse> reply = calls.call(CallKind.DRAFT, null, request);
        if (reply.isErr()) {
            log.warn("Batch eval call failed size={}", batch.items().size());
            return failed(batch);
        }
        final ChatResponse response = Objects.requireNonNull(reply.data());
        final BatchReply parsed = parser.parse(response.content(), batch.items(), SOURCE, TARGET);
        return measured(batch, response, parsed);
    }

    private static BatchEvalRow measured(final Batch batch, final ChatResponse response, final BatchReply parsed) {
        final List<BatchItem> items = batch.items();
        final int tokenPass = (int) items.stream()
                .filter(item -> kept(item, parsed.outcome(item.id()).orElseThrow()))
                .count();
        final TokenUsage usage = response.usage();
        return new BatchEvalRow(
                items.size(),
                (int) (parsed.count(ItemStatus.OK) + parsed.count(ItemStatus.MERGED_SUSPECT)),
                tokenPass,
                (int) parsed.count(ItemStatus.MISSING),
                (int) parsed.count(ItemStatus.DUPLICATE),
                (int) parsed.count(ItemStatus.MERGED_SUSPECT),
                (int) parsed.count(ItemStatus.EXTRA),
                usage != null && usage.completion() != null
                        ? usage.completion()
                        : TokenEstimator.estimate(response.content(), TARGET),
                usage != null && usage.prompt() != null ? usage.prompt() : -1,
                false);
    }

    /** The token gate of the whole eval: the id came back once and {@link ReplyChecks#gate} passes on its text. */
    private static boolean kept(final BatchItem item, final ItemOutcome outcome) {
        return outcome.status() == ItemStatus.OK && ReplyChecks.gate(item.masked(), outcome.target()) == Check.PASS;
    }

    private static BatchEvalRow failed(final Batch batch) {
        return new BatchEvalRow(batch.items().size(), 0, 0, batch.items().size(), 0, 0, 0, 0, -1, true);
    }
}
