package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.batch.ItemOutcome;
import ua.bookloom.pipeline.batch.ItemProblem;
import ua.bookloom.pipeline.batch.ItemStatus;
import ua.bookloom.pipeline.batch.ProtocolLeak;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.eval.BatchEvalCases.Batch;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

/**
 * Sends the batches of {@link BatchEvalCases} to a real model as a run sends them: the batch's context and request are
 * made by the run's own {@link ua.bookloom.pipeline.run.PromptRequests} (its previous pairs, next source, glossary
 * lines, character sheet and window fit), and the reply is read by the production parser and measured before any
 * fallback a run would make.
 */
@Slf4j
final class BatchEvalRunner {

    private static final String SOURCE = PromptEvalCases.SOURCE_LANGUAGE;
    private static final String TARGET = PromptEvalCases.TARGET_LANGUAGE;

    private final ModelCalls calls;
    private final BatchReplyParser parser = new BatchReplyParser(new ObjectMapper());

    BatchEvalRunner(final ModelCalls calls) {
        this.calls = Objects.requireNonNull(calls, "calls");
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

    /**
     * Runs the whole case list the way a job sizes its batches: the first batch has {@code initialSize} cases, and each
     * reply is fed to a production {@link BatchDrafter} whose size halves on a lost numbering, an omission or a merge and
     * grows after a clean streak. A tail shorter than the smallest batching size is left out, as in the fixed sweep.
     */
    List<BatchEvalRow> runAdaptive(final int initialSize) {
        final int total = BatchEvalCases.drafts().size();
        final BatchDrafter sizing = sizing(initialSize);
        final List<BatchEvalRow> rows = new ArrayList<>();
        int start = 0;
        while (total - start >= BatchDrafter.MIN_BATCHING_SIZE) {
            final int size = Math.min(Math.max(sizing.size(), BatchDrafter.MIN_BATCHING_SIZE), total - start);
            final Outcome outcome = outcome(BatchEvalCases.batchAt(start, size));
            rows.add(outcome.row());
            outcome.reply().ifPresent(sizing::record);
            log.debug("Adaptive batch start={} size={} nextSize={}", start, size, sizing.size());
            start += size;
        }
        return rows;
    }

    private BatchDrafter sizing(final int initialSize) {
        final EvalProject any = project(BatchEvalCases.batchAt(0, BatchDrafter.MIN_BATCHING_SIZE));
        return new BatchDrafter(
                new BatchPromptBuilder(new PromptTemplates(), any.frame()), parser, calls, SOURCE, TARGET, initialSize);
    }

    BatchEvalRow run(final Batch batch) {
        return outcome(batch).row();
    }

    /** One batch's row, and the parsed reply the job's batch size is adapted by (none when the call failed). */
    private record Outcome(BatchEvalRow row, Optional<BatchReply> reply) {}

    private Outcome outcome(final Batch batch) {
        log.info("Batch eval size={}", batch.cases().size());
        final EvalProject project = project(batch);
        final Optional<PreparedBatch> prepared = project.batch();
        if (prepared.isEmpty()) {
            log.warn(
                    "Batch eval: no batch fits the window size={}",
                    batch.cases().size());
            return new Outcome(failed(batch.cases().size()), Optional.empty());
        }
        final List<BatchItem> items = prepared.get().items();
        final ChatRequest request = project.batchRequest(prepared.get());
        final List<String> segmentIds =
                prepared.get().shown().stream().map(slot -> slot.segment().id()).toList();
        final Result<ChatResponse> reply = calls.callAbout(CallKind.DRAFT, segmentIds, request);
        if (reply.isErr()) {
            log.warn("Batch eval call failed size={}", items.size());
            return new Outcome(failed(items.size()), Optional.empty());
        }
        final ChatResponse response = Objects.requireNonNull(reply.data());
        final BatchReply parsed = parser.parse(response.content(), items, SOURCE, TARGET);
        return new Outcome(measured(project, items, response, parsed), Optional.of(parsed));
    }

    private EvalProject project(final Batch batch) {
        final Map<String, EvalTerm> glossary = new LinkedHashMap<>();
        batch.cases().forEach(draft -> draft.glossary().forEach(term -> glossary.putIfAbsent(term.term(), term)));
        return EvalProject.of(
                new EvalProject.Setup(
                        SOURCE,
                        TARGET,
                        null,
                        List.copyOf(glossary.values()),
                        new EvalContext(batch.earlier(), null, List.of(), null),
                        batch.cases().stream().map(Draft::masked).toList(),
                        batch.next() == null ? List.of() : List.of(batch.next().masked())),
                calls);
    }

    private static BatchEvalRow measured(
            final EvalProject project,
            final List<BatchItem> items,
            final ChatResponse response,
            final BatchReply parsed) {
        final int tokenPass = (int) IntStream.range(0, items.size())
                .filter(i -> kept(project, i, parsed.outcome(items.get(i).id()).orElseThrow()))
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
                (int) parsed.outcomes().stream()
                        .filter(outcome -> outcome.problems().contains(ItemProblem.TOO_SHORT))
                        .count(),
                (int) parsed.outcomes().stream()
                        .filter(outcome -> outcome.status() == ItemStatus.OK && ProtocolLeak.leaks(outcome.target()))
                        .count(),
                usage != null && usage.completion() != null
                        ? usage.completion()
                        : TokenEstimator.estimate(response.content(), TARGET),
                usage != null && usage.prompt() != null ? usage.prompt() : -1,
                false);
    }

    /** The token gate of the whole eval: the id came back once and the run's own gate and checks pass on its text. */
    private static boolean kept(final EvalProject project, final int index, final ItemOutcome outcome) {
        return outcome.status() == ItemStatus.OK
                && ReplyJudge.judge(project, index, outcome.target()).gatePassed();
    }

    private static BatchEvalRow failed(final int size) {
        return new BatchEvalRow(size, 0, 0, size, 0, 0, 0, 0, 0, 0, -1, true);
    }
}
