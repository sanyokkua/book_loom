package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.eval.EvalCase.Expect;
import ua.bookloom.pipeline.eval.EvalCase.Fix;
import ua.bookloom.pipeline.eval.EvalCase.Judge;
import ua.bookloom.pipeline.eval.EvalCase.Suggest;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.judge.JudgeCall;
import ua.bookloom.pipeline.judge.JudgeReplyParser;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.judge.JudgedPair;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * Sends every case through the production prompt builders — {@link DraftPromptBuilder}, {@link DirectedFix} and
 * {@link JudgeCall} — to a real model, and measures each reply before any repair the run would make.
 */
@Slf4j
final class PromptEvalRunner {

    private static final double GOOD_FLOOR = 0.8;
    private static final double BAD_CEILING = 0.6;

    private final ModelCalls calls;
    private final CallFrame frame = new CallFrame(
            PromptEvalCases.SOURCE_LANGUAGE,
            PromptEvalCases.TARGET_LANGUAGE,
            StyleSheet.from(BookBrief.defaults(PromptEvalCases.SOURCE_LANGUAGE)),
            ForeignPassagePolicy.KEEP);
    private final PromptTemplates templates = new PromptTemplates();
    private final DraftReplyParser parser = new DraftReplyParser(new ObjectMapper());
    private final DraftPromptBuilder builder = new DraftPromptBuilder(templates, frame);
    private final DirectedFix directedFix = new DirectedFix(templates, parser);
    private final JudgeCall judgeCall = new JudgeCall(templates, new JudgeReplyParser(new ObjectMapper()));

    PromptEvalRunner(final ModelCalls calls) {
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    List<EvalRow> runAll(final List<EvalCase> cases) {
        return cases.stream().flatMap(evalCase -> run(evalCase).stream()).toList();
    }

    /** Judges every corpus case {@code repeats} times and records whether the verdicts agree. */
    List<DefectRow> runDefects(final List<DefectCase> corpus, final int repeats) {
        return corpus.stream().map(defect -> defect(defect, repeats)).toList();
    }

    private DefectRow defect(final DefectCase defect, final int repeats) {
        log.info("Corpus case {} x{}", defect.id(), repeats);
        final List<JudgeVerdict> verdicts = java.util.stream.IntStream.range(0, Math.max(1, repeats))
                .mapToObj(run -> judgeOne(defect.source(), defect.candidate()))
                .toList();
        final boolean first = refused(verdicts.get(0), defect.defective());
        return new DefectRow(
                defect.id(),
                defect.kind(),
                defect.defective(),
                first,
                verdicts.stream().allMatch(JudgeVerdict::readable),
                verdicts.stream().allMatch(verdict -> refused(verdict, defect.defective()) == first),
                verdicts.size());
    }

    /** An unreadable verdict is always wrong: a defect not caught, a clean text refused. */
    private static boolean refused(final JudgeVerdict verdict, final boolean defective) {
        return verdict.readable() ? verdict.score() < GOOD_FLOOR : !defective;
    }

    private List<EvalRow> run(final EvalCase evalCase) {
        log.info("Prompt eval case {}", evalCase.name());
        return switch (evalCase) {
            case Draft draft -> List.of(draft(draft));
            case Fix fix -> List.of(fix(fix));
            case Judge judge -> List.of(judge(judge));
            case Suggest suggest -> SuggestEval.rows(suggest, frame, calls);
        };
    }

    private EvalRow draft(final Draft draft) {
        final ChatRequest request = ChatRequests.build(
                PromptName.DRAFT,
                builder.messagesFor(
                        segment(draft.masked()), new DraftContext(List.of(), null, draft.glossary(), List.of())),
                OutputLimit.forSource(draft.masked(), frame.sourceLanguage(), frame.targetLanguage()),
                false);
        final Result<ChatResponse> reply = calls.call(CallKind.DRAFT, draft.name(), request);
        if (reply.isErr()) {
            return failed(
                    draft.name(),
                    "draft",
                    "call failed: " + Objects.requireNonNull(reply.error()).code(),
                    draft.expect());
        }
        final String content = Objects.requireNonNull(reply.data()).content();
        final ParsedReply parsed = parser.parse(content);
        if (parsed.kind() != ReplyKind.STRUCTURED) {
            return failed(draft.name(), "draft", "unparsed: " + content, draft.expect());
        }
        return measured(draft.name(), "draft", draft.masked(), parsed.translation(), draft.expect());
    }

    private EvalRow fix(final Fix fix) {
        final Result<RepairReply> reply =
                directedFix.fix(segment(fix.masked()), frame, fix.masked(), fix.rejected(), fix.findings(), calls);
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

    private EvalRow judge(final Judge judge) {
        final JudgeVerdict good = judgeOne(judge.masked(), judge.good());
        final JudgeVerdict bad = judgeOne(judge.masked(), judge.bad());
        final boolean readable = good.readable() && bad.readable();
        final boolean separated = readable && good.score() >= GOOD_FLOOR && bad.score() <= BAD_CEILING;
        final String detail = "good=" + good.score() + " " + good.findings().size() + "f, bad=" + bad.score() + " "
                + bad.findings().size() + "f";
        return new EvalRow(
                judge.name(),
                "judge",
                Check.of(readable),
                Check.NA,
                Check.NA,
                Check.NA,
                Check.NA,
                Check.of(separated),
                detail);
    }

    private JudgeVerdict judgeOne(final String masked, final String candidate) {
        final Result<JudgeVerdict> verdict =
                judgeCall.judge(List.of(new JudgedPair("Eval:0", masked, candidate)), frame, List.of(), calls);
        return verdict.isOk() ? Objects.requireNonNull(verdict.data()) : JudgeVerdict.unreadable();
    }

    private static EvalRow measured(
            final String name, final String kind, final String masked, final String target, final Expect expect) {
        return new EvalRow(
                name,
                kind,
                Check.PASS,
                ReplyChecks.gate(masked, target),
                ReplyChecks.script(masked, target, expect),
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
