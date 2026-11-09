package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.eval.EvalCase.Expect;
import ua.bookloom.pipeline.eval.EvalCase.Fix;
import ua.bookloom.pipeline.eval.EvalCase.Repair;
import ua.bookloom.pipeline.eval.EvalCase.RepairStep;
import ua.bookloom.pipeline.eval.EvalCase.Suggest;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.PromptRequests;

/**
 * Sends every case to a real model as a run would: the request of each case is built by the run's own
 * {@link PromptRequests} over an {@link EvalProject} (so the context, the glossary lines, the mask and the window are
 * the app's), the directed fix is the production class given the production inputs, and each reply is measured before
 * any repair the run would make.
 */
@Slf4j
final class PromptEvalRunner {

    private final ModelCalls calls;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final PromptTemplates templates = EvalVariants.templates();
    private final DraftReplyParser parser = new DraftReplyParser(new ObjectMapper());
    private final DirectedFix directedFix = new DirectedFix(templates, parser);
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

    /** Runs a language corpus: its drafts. */
    List<EvalRow> runLanguage(final LanguageCorpus corpus) {
        return corpus.drafts().stream().map(this::draft).toList();
    }

    private List<EvalRow> run(final EvalCase evalCase) {
        log.info("Prompt eval case {}", evalCase.name());
        return switch (evalCase) {
            case Draft draft -> List.of(draft(draft));
            case Fix fix -> List.of(fix(fix));
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
        return read(project, draft.name(), "draft", shown, reply, draft.expect());
    }

    private EvalRow repair(final Repair repair) {
        final EvalProject project = project(repair.masked());
        final Optional<ChatRequest> request = repairRequest(project, repair.step(), repair.rejected());
        if (request.isEmpty()) {
            return failed(repair.name(), "repair", "the gate accepts the rejected target", repair.expect());
        }
        return read(
                project,
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
        final TranslateContext context = project.translateContext(0);
        final ProtectedMask mask = project.mask(0);
        final PromptRequests requests = project.requests();
        return step == RepairStep.STRUCTURAL
                ? Optional.of(requests.formatReaskRequest(segment, context, mask, rejected))
                : requests.repairRequest(segment, context, mask, rejected);
    }

    static CallKind repairKind(final RepairStep step) {
        return step == RepairStep.STRUCTURAL ? CallKind.STRUCTURAL_REPAIR : CallKind.REPAIR;
    }

    private EvalRow read(
            final EvalProject project,
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
        final ParsedReply parsed = parser.parse(content, shown);
        if (parsed.kind() != ReplyKind.STRUCTURED) {
            return failed(name, kind, "unparsed: " + content, expect);
        }
        return measured(project, name, kind, shown, parsed.translation(), expect);
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
                measured(project, fix.name(), "fix", fix.masked(), rewritten.maskedTarget(), fix.expect());
            case RepairReply.Malformed malformed ->
                failed(fix.name(), "fix", "unparsed: " + malformed.diagnostic(), fix.expect());
            case RepairReply.FlagNow flagNow ->
                failed(fix.name(), "fix", "flagged: " + flagNow.error().code(), fix.expect());
        };
    }

    private EvalRow measured(
            final EvalProject project,
            final String name,
            final String kind,
            final String masked,
            final String target,
            final Expect expect) {
        final ReplyJudge.Verdict verdict = ReplyJudge.judge(project, 0, target);
        final Check script =
                expect.copy() ? Check.of(target.strip().equals(masked.strip())) : Check.of(verdict.accepted());
        return new EvalRow(
                name,
                kind,
                Check.PASS,
                Check.of(verdict.gatePassed()),
                script,
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
