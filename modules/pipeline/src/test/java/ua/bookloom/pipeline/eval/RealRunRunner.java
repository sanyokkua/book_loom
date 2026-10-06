package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.batch.ItemProblem;
import ua.bookloom.pipeline.batch.ItemStatus;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.pipeline.eval.ReviewerEval.BatchReviewed;
import ua.bookloom.pipeline.eval.ReviewerEval.Reviewed;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

/**
 * Sends the real-run corpus (15e.2) to a real model as a run sends it — every request comes from the run's own
 * {@link ua.bookloom.pipeline.run.PromptRequests} over an {@link EvalProject} — and measures each reply by the
 * production checks: the text checks and the gender and length checks on a draft, the production reviewer with its
 * edits verified on a pair or a whole chunk, the batch parser and its item validator on a batch, the document gate on a
 * repair. A reviewer call is repeated {@code repeats} times to measure its stability.
 */
@Slf4j
final class RealRunRunner {

    private static final String SOURCE = RunCorpus.SOURCE_LANGUAGE;
    private static final String TARGET = RunCorpus.TARGET_LANGUAGE;

    private final ModelCalls calls;
    private final int repeats;
    private final Path workDir;
    private final DraftReplyParser parser = new DraftReplyParser(new ObjectMapper());
    private final BatchReplyParser batchParser = new BatchReplyParser(new ObjectMapper());
    private final ReviewerEval reviews;
    private final DocumentPort documents = TestDocuments.documents();

    /**
     * Creates a runner.
     *
     * @param calls the seam every model call goes through
     * @param repeats how many times a reviewer call is made, at least one
     * @param workDir a directory the repair cases write their Markdown source into
     */
    RealRunRunner(final ModelCalls calls, final int repeats, final Path workDir) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.repeats = Math.max(1, repeats);
        this.workDir = Objects.requireNonNull(workDir, "workDir");
        final PromptTemplates templates = new PromptTemplates();
        this.reviews = new ReviewerEval(calls, templates, new DirectedFix(templates, parser));
    }

    /** Runs every case whose id starts with {@code only} (every case when it is empty). */
    List<RealRunRow> runAll(final String only) {
        final List<RealRunRow> rows = new ArrayList<>();
        RunCorpus.text().stream().filter(c -> c.id().startsWith(only)).forEach(c -> rows.addAll(text(c)));
        RunCorpus.reviewerBatches().stream()
                .filter(c -> c.id().startsWith(only))
                .forEach(c -> rows.addAll(reviewerBatch(c)));
        RunCorpus.batches().stream().filter(c -> c.id().startsWith(only)).forEach(c -> rows.add(batch(c)));
        RunCorpus.repairs().stream().filter(c -> c.id().startsWith(only)).forEach(c -> rows.add(repair(c)));
        return rows;
    }

    List<RealRunRow> text(final RunCase runCase) {
        log.info("Real-run case {} kind={}", runCase.id(), runCase.kind());
        final RunVerdicts verdicts = RunVerdicts.of(runCase);
        final List<RealRunRow> rows = new ArrayList<>();
        if (runCase.isDeterministic()) {
            rows.add(draft(runCase, verdicts));
        }
        rows.add(separation(runCase, verdicts));
        return rows;
    }

    // The draft is the run's own first request; its reply passes when the run's production checks leave it alone.
    private RealRunRow draft(final RunCase runCase, final RunVerdicts verdicts) {
        final EvalProject project = verdicts.project();
        final Result<ChatResponse> reply =
                calls.call(CallKind.DRAFT, project.segment(0).id(), project.draftRequest(0));
        if (reply.isErr()) {
            return RealRunRow.of(
                    runCase.id(),
                    runCase.kind(),
                    "draft",
                    false,
                    runCase.knownFailure(),
                    "call failed: " + Objects.requireNonNull(reply.error()).code());
        }
        final ParsedReply parsed =
                parser.parse(Objects.requireNonNull(reply.data()).content());
        if (parsed.kind() != ReplyKind.STRUCTURED) {
            return RealRunRow.of(runCase.id(), runCase.kind(), "draft", false, runCase.knownFailure(), "unparsed");
        }
        final String target = parsed.translation();
        final boolean passed =
                ReplyChecks.gate(project.mask(0).maskedText(), target) == Check.PASS && !verdicts.fires(target);
        return RealRunRow.of(runCase.id(), runCase.kind(), "draft", passed, runCase.knownFailure(), target);
    }

    /** What a gate or the reviewer made of a good and a bad candidate. */
    private record Separation(boolean goodFlagged, boolean badFlagged, String detail) {

        boolean isSeparated(final boolean hasBad) {
            return !goodFlagged && (!hasBad || badFlagged);
        }
    }

    private RealRunRow separation(final RunCase runCase, final RunVerdicts verdicts) {
        final List<Separation> runs = IntStream.range(0, repeats)
                .mapToObj(run -> separate(runCase, verdicts))
                .toList();
        final Separation first = runs.getFirst();
        final boolean agreed = runs.stream().allMatch(run -> run.equals(first));
        return RealRunRow.of(
                        runCase.id(),
                        runCase.kind(),
                        "review",
                        first.isSeparated(runCase.bad() != null),
                        runCase.knownFailure(),
                        first.detail())
                .withCall(agreed, false);
    }

    private Separation separate(final RunCase runCase, final RunVerdicts verdicts) {
        final Flag good = flag(runCase, verdicts, runCase.good());
        final String bad = runCase.bad();
        final Flag defective = bad == null ? new Flag(false, "-") : flag(runCase, verdicts, bad);
        return new Separation(good.flagged(), defective.flagged(), "good: " + good.how() + "; bad: " + defective.how());
    }

    private record Flag(boolean flagged, String how) {}

    // A candidate the run's own check refuses never reaches the reviewer, so it is not asked.
    private Flag flag(final RunCase runCase, final RunVerdicts verdicts, final String candidate) {
        if (runCase.isDeterministic() && verdicts.fires(candidate)) {
            return new Flag(true, "gate");
        }
        final Reviewed reviewed = reviews.review(verdicts.project(), candidate);
        return new Flag(reviewed.flagged(), reviewed.detail());
    }

    List<RealRunRow> reviewerBatch(final ReviewerBatchCase batchCase) {
        log.info(
                "Real-run reviewer batch {} pairs={}",
                batchCase.id(),
                batchCase.pairs().size());
        final EvalProject project = EvalProject.of(batchCase.setup(SOURCE, TARGET), calls);
        final List<String> candidates = batchCase.pairs().stream()
                .map(ReviewerBatchCase.Pair::candidate)
                .toList();
        final List<BatchReviewed> runs = IntStream.range(0, repeats)
                .mapToObj(run -> reviews.reviewAll(project, candidates))
                .toList();
        final boolean truncated = runs.stream().anyMatch(BatchReviewed::truncated);
        final List<RealRunRow> rows = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            rows.add(pairRow(batchCase, runs, i).withCall(isStable(runs, i), truncated && i == 0));
        }
        return rows;
    }

    private static RealRunRow pairRow(final ReviewerBatchCase batchCase, final List<BatchReviewed> runs, final int i) {
        final Reviewed first = runs.getFirst().items().get(i);
        final boolean expected = batchCase.pairs().get(i).defective();
        return RealRunRow.of(
                batchCase.id() + "#" + (i + 1),
                batchCase.kind(),
                "review-batch",
                first.readable() && first.flagged() == expected,
                false,
                (expected ? "defective: " : "clean: ") + first.detail());
    }

    private static boolean isStable(final List<BatchReviewed> runs, final int i) {
        final String first = runs.getFirst().items().get(i).signature();
        return runs.stream().allMatch(run -> run.items().get(i).signature().equals(first));
    }

    RealRunRow batch(final BatchCase batchCase) {
        log.info("Real-run batch {} items={}", batchCase.id(), batchCase.chunk().size());
        final EvalProject project = EvalProject.of(batchCase.setup(SOURCE, TARGET), calls);
        final Optional<PreparedBatch> prepared = project.batch();
        if (prepared.isEmpty()) {
            return RealRunRow.batch(
                    batchCase.id(), batchCase.kind(), false, batchCase.chunk().size(), 0, 0, "no batch fits");
        }
        final List<String> ids =
                prepared.get().shown().stream().map(slot -> slot.segment().id()).toList();
        final Result<ChatResponse> reply = calls.callAbout(CallKind.DRAFT, ids, project.batchRequest(prepared.get()));
        if (reply.isErr()) {
            return RealRunRow.batch(
                    batchCase.id(),
                    batchCase.kind(),
                    false,
                    prepared.get().items().size(),
                    0,
                    0,
                    "call failed: " + Objects.requireNonNull(reply.error()).code());
        }
        final BatchReply parsed = batchParser.parse(
                Objects.requireNonNull(reply.data()).content(), prepared.get().items(), SOURCE, TARGET);
        return measured(batchCase, prepared.get().items().size(), parsed);
    }

    private static RealRunRow measured(final BatchCase batchCase, final int items, final BatchReply parsed) {
        final int tooShort = (int) parsed.outcomes().stream()
                .filter(outcome -> outcome.problems().contains(ItemProblem.TOO_SHORT))
                .count();
        final int leaked = (int) parsed.outcomes().stream()
                .filter(outcome -> outcome.status() == ItemStatus.OK && LeakCheck.leaks(outcome.target()))
                .count();
        final long reported = parsed.outcomes().stream()
                .filter(outcome -> !outcome.terms().isEmpty())
                .count();
        final boolean passed = parsed.readable() && parsed.failingIds().isEmpty() && leaked == 0;
        return RealRunRow.batch(
                batchCase.id(),
                batchCase.kind(),
                passed,
                items,
                tooShort,
                leaked,
                "accepted=" + parsed.acceptedIds().size() + "/" + items + " tooShort=" + tooShort + " leaked=" + leaked
                        + " termsReported=" + reported);
    }

    RealRunRow repair(final RepairCase repairCase) {
        log.info("Real-run repair {} step={}", repairCase.id(), repairCase.step());
        final Segment real = documentSegment(repairCase);
        final EvalProject project = EvalProject.of(
                EvalProject.Setup.single(SOURCE, TARGET, List.of(), EvalContext.none(), real.masked()), calls);
        final String call = "repair-" + repairCase.step().name().toLowerCase(java.util.Locale.ROOT);
        final Optional<ChatRequest> request =
                PromptEvalRunner.repairRequest(project, repairCase.step(), repairCase.rejected());
        if (request.isEmpty()) {
            return RealRunRow.of(repairCase.id(), "repair", call, false, false, "the gate accepts the rejected target");
        }
        final Result<ChatResponse> reply = calls.call(
                PromptEvalRunner.repairKind(repairCase.step()),
                project.segment(0).id(),
                request.get());
        if (reply.isErr()) {
            return RealRunRow.of(
                    repairCase.id(),
                    "repair",
                    call,
                    false,
                    false,
                    "call failed: " + Objects.requireNonNull(reply.error()).code());
        }
        final ParsedReply parsed =
                parser.parse(Objects.requireNonNull(reply.data()).content());
        final boolean restored = parsed.kind() == ReplyKind.STRUCTURED && restores(real, parsed.translation());
        return RealRunRow.of(repairCase.id(), "repair", call, restored, false, parsed.translation());
    }

    /** Whether the production document gate takes {@code maskedTarget} for the real segment. */
    boolean restores(final Segment segment, final String maskedTarget) {
        final String restoredWhitespace = WhitespaceRestoration.restore(segment.masked(), maskedTarget.strip());
        return GateFunction.of(documents, BookFormat.MARKDOWN).restore(segment, restoredWhitespace)
                instanceof GateResult.Restored;
    }

    /** The case's paragraph as the real document module parses it. */
    Segment documentSegment(final RepairCase repairCase) {
        final Path file = TestBooks.markdown(workDir.resolve(repairCase.id() + ".md"), repairCase.markdown());
        return Objects.requireNonNull(documents.open(file).data(), repairCase.id())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }
}
