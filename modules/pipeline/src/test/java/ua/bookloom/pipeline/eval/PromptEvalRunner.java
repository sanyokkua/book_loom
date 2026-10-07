package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.eval.EvalCase.Expect;
import ua.bookloom.pipeline.eval.EvalCase.Fix;
import ua.bookloom.pipeline.eval.EvalCase.Repair;
import ua.bookloom.pipeline.eval.EvalCase.RepairStep;
import ua.bookloom.pipeline.eval.EvalCase.Review;
import ua.bookloom.pipeline.eval.EvalCase.Suggest;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.QaEvaluation;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.run.PromptRequests;

/**
 * Sends every case to a real model as a run would: the request of each case is built by the run's own
 * {@link PromptRequests} over an {@link EvalProject} (so the context, the glossary lines, the mask and the window are
 * the app's), the reviewer and the directed fix are the production classes given the production inputs, and each reply
 * is measured before any repair the run would make. A reviewer reply is measured the way the run uses it: its edits go
 * through the production {@link EditApplier}.
 */
@Slf4j
final class PromptEvalRunner {

    private final ModelCalls calls;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final PromptTemplates templates = new PromptTemplates();
    private final DraftReplyParser parser = new DraftReplyParser(new ObjectMapper());
    private final DirectedFix directedFix = new DirectedFix(templates, parser);
    private final ReviewerEval reviews;
    private final int window;

    PromptEvalRunner(final ModelCalls calls) {
        this(calls, PromptEvalCases.SOURCE_LANGUAGE, PromptEvalCases.TARGET_LANGUAGE);
    }

    /** A runner for one language pair; the language corpora run one of these per target language. */
    PromptEvalRunner(final ModelCalls calls, final String sourceLanguage, final String targetLanguage) {
        this(calls, sourceLanguage, targetLanguage, EvalProject.window());
    }

    /** A runner whose requests are sized against a given window instead of the environment's. */
    PromptEvalRunner(
            final ModelCalls calls, final String sourceLanguage, final String targetLanguage, final int window) {
        this.window = window;
        this.calls = Objects.requireNonNull(calls, "calls");
        this.sourceLanguage = Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        this.reviews = new ReviewerEval(calls, templates, directedFix);
    }

    private EvalProject project(final String text, final List<EvalTerm> glossary, final EvalContext context) {
        return EvalProject.of(
                EvalProject.Setup.single(sourceLanguage, targetLanguage, glossary, context, text), calls, window);
    }

    private EvalProject project(final String text) {
        return project(text, List.of(), EvalContext.none());
    }

    List<EvalRow> runAll(final List<EvalCase> cases) {
        return cases.stream().flatMap(evalCase -> run(evalCase).stream()).toList();
    }

    /** Runs a language corpus: its drafts, then its reviewer cases. */
    LanguageRun runLanguage(final LanguageCorpus corpus, final int repeats) {
        final List<EvalRow> rows = corpus.drafts().stream().map(this::draft).toList();
        return new LanguageRun(rows, runDefects(corpus.reviews(), repeats));
    }

    /** The outcome of one language corpus. */
    record LanguageRun(List<EvalRow> rows, List<DefectRow> defectRows) {

        /** Copies the lists. */
        LanguageRun {
            rows = List.copyOf(rows);
            defectRows = List.copyOf(defectRows);
        }
    }

    /** Reviews every corpus case {@code repeats} times and records whether the replies agree. */
    List<DefectRow> runDefects(final List<DefectCase> corpus, final int repeats) {
        return corpus.stream().map(defect -> defect(defect, repeats)).toList();
    }

    private DefectRow defect(final DefectCase defect, final int repeats) {
        log.info("Corpus case {} x{}", defect.id(), repeats);
        final EvalProject project = project(defect.source());
        final Checked checked = checkedBy(project, defect);
        if (checked.refused()) {
            // The run never shows a candidate a check refuses to the reviewer, so asking would measure nothing.
            return new DefectRow(
                    defect.id(), defect.kind(), defect.defective(), true, true, true, Math.max(1, repeats), 0, false);
        }
        final List<ReviewerEval.Reviewed> outcomes = IntStream.range(0, Math.max(1, repeats))
                .mapToObj(run -> reviews.review(project, defect.candidate()))
                .toList();
        final boolean byReviewer = outcomes.get(0).flagged();
        return new DefectRow(
                defect.id(),
                defect.kind(),
                defect.defective(),
                byReviewer || checked.noted(),
                outcomes.stream().allMatch(ReviewerEval.Reviewed::readable),
                outcomes.stream()
                        .allMatch(review ->
                                review.signature().equals(outcomes.get(0).signature())),
                outcomes.size(),
                outcomes.stream().mapToInt(ReviewerEval.Reviewed::tokenBreaks).sum(),
                byReviewer);
    }

    /** What the run's own checks say about a candidate before any reviewer sees it. */
    private record Checked(boolean refused, boolean noted) {}

    private Checked checkedBy(final EvalProject project, final DefectCase defect) {
        final CallFrame frame = project.frame();
        final var drafted = new DraftOutcome.Drafted(
                project.segment(0),
                defect.source(),
                List.of(),
                defect.candidate(),
                defect.candidate(),
                defect.candidate(),
                null);
        final var qa = QaEvaluation.evaluate(
                List.of(),
                drafted.segment(),
                defect.source(),
                defect.candidate(),
                defect.candidate(),
                frame,
                project.settings().names(),
                List.of(),
                List.of());
        final boolean noted = !TextChecks.run(
                        DisplayText.of(defect.source()),
                        DisplayText.of(defect.candidate()),
                        frame.sourceLanguage(),
                        frame.targetLanguage())
                .isEmpty();
        return new Checked(!qa.hardGatesPass() || qa.failedOutright(), noted);
    }

    private List<EvalRow> run(final EvalCase evalCase) {
        log.info("Prompt eval case {}", evalCase.name());
        return switch (evalCase) {
            case Draft draft -> List.of(draft(draft));
            case Fix fix -> List.of(fix(fix));
            case Review review -> List.of(review(review));
            case Repair repair -> List.of(repair(repair));
            case Suggest suggest ->
                SuggestEval.rows(
                        suggest, project(suggest.sentences().getFirst()).frame(), calls);
        };
    }

    private EvalRow draft(final Draft draft) {
        final EvalProject project = project(draft.masked(), draft.glossary(), draft.context());
        final String shown = project.mask(0).maskedText();
        final Result<ChatResponse> reply =
                calls.call(CallKind.DRAFT, project.segment(0).id(), project.draftRequest(0));
        return read(draft.name(), "draft", shown, reply, draft.expect());
    }

    private EvalRow repair(final Repair repair) {
        final EvalProject project = project(repair.masked());
        final Optional<ChatRequest> request = repairRequest(project, repair.step(), repair.rejected());
        if (request.isEmpty()) {
            return failed(repair.name(), "repair", "the gate accepts the rejected target", repair.expect());
        }
        return read(
                repair.name(),
                "repair",
                project.mask(0).maskedText(),
                calls.call(repairKind(repair.step()), project.segment(0).id(), request.get()),
                repair.expect());
    }

    /** The request a run sends to repair the project's first segment after {@code rejected}, or empty when none is needed. */
    static Optional<ChatRequest> repairRequest(
            final EvalProject project, final RepairStep step, final String rejected) {
        final Segment segment = project.segment(0);
        final DraftContext context = project.draftContext(0).draftContext();
        final ProtectedMask mask = project.mask(0);
        final PromptRequests requests = project.requests();
        return step == RepairStep.STRUCTURAL
                ? Optional.of(requests.structuralRepairRequest(segment, context, mask, rejected))
                : requests.placeholderRepairRequest(segment, context, mask, rejected);
    }

    static CallKind repairKind(final RepairStep step) {
        return step == RepairStep.STRUCTURAL ? CallKind.STRUCTURAL_REPAIR : CallKind.PLACEHOLDER_REPAIR;
    }

    private EvalRow read(
            final String name,
            final String kind,
            final String shown,
            final Result<ChatResponse> reply,
            final Expect expect) {
        if (reply.isErr()) {
            return failed(
                    name,
                    kind,
                    "call failed: " + Objects.requireNonNull(reply.error()).code(),
                    expect);
        }
        final String content = Objects.requireNonNull(reply.data()).content();
        final ParsedReply parsed = parser.parse(content);
        if (parsed.kind() != ReplyKind.STRUCTURED) {
            return failed(name, kind, "unparsed: " + content, expect);
        }
        return measured(name, kind, shown, parsed.translation(), expect);
    }

    private EvalRow fix(final Fix fix) {
        final EvalProject project = project(fix.masked());
        final Result<RepairReply> reply = directedFix.fix(
                project.segment(0),
                project.frame(),
                project.mask(0).maskedText(),
                fix.rejected(),
                fix.findings(),
                calls);
        if (reply.isErr()) {
            return failed(
                    fix.name(),
                    "fix",
                    "call failed: " + Objects.requireNonNull(reply.error()).code(),
                    fix.expect());
        }
        return switch (Objects.requireNonNull(reply.data())) {
            case RepairReply.Rewritten rewritten ->
                measured(fix.name(), "fix", fix.masked(), rewritten.maskedTarget(), fix.expect());
            case RepairReply.Malformed malformed ->
                failed(fix.name(), "fix", "unparsed: " + malformed.diagnostic(), fix.expect());
            case RepairReply.FlagNow flagNow ->
                failed(fix.name(), "fix", "flagged: " + flagNow.error().code(), fix.expect());
        };
    }

    private EvalRow review(final Review review) {
        final EvalProject project = project(review.masked());
        final ReviewerEval.Reviewed good = reviews.review(project, review.good());
        final ReviewerEval.Reviewed bad = reviews.review(project, review.bad());
        final boolean readable = good.readable() && bad.readable();
        final boolean separated = readable && !good.flagged() && bad.flagged();
        return new EvalRow(
                review.name(),
                "review",
                Check.of(readable),
                Check.NA,
                Check.NA,
                Check.NA,
                Check.NA,
                Check.of(separated),
                "good: " + good.detail() + "; bad: " + bad.detail());
    }

    private EvalRow measured(
            final String name, final String kind, final String masked, final String target, final Expect expect) {
        return new EvalRow(
                name,
                kind,
                Check.PASS,
                ReplyChecks.gate(masked, target),
                ReplyChecks.script(masked, target, expect, targetLanguage),
                ReplyChecks.marker(target, expect),
                ReplyChecks.injection(target, expect),
                Check.NA,
                target);
    }

    private static EvalRow failed(final String name, final String kind, final String detail, final Expect expect) {
        final Check marker = expect.marker().isEmpty() ? Check.NA : Check.FAIL;
        final Check injection = expect.injection() ? Check.FAIL : Check.NA;
        return new EvalRow(name, kind, Check.FAIL, Check.FAIL, Check.FAIL, marker, injection, Check.NA, detail);
    }

    static Segment segment(final String masked) {
        return new Segment(
                "Eval:0",
                "Eval",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
