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
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.eval.EvalCase.Expect;
import ua.bookloom.pipeline.eval.EvalCase.Fix;
import ua.bookloom.pipeline.eval.EvalCase.Review;
import ua.bookloom.pipeline.eval.EvalCase.Suggest;
import ua.bookloom.pipeline.eval.EvalRow.Check;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.QaEvaluation;
import ua.bookloom.pipeline.heal.RepairReply;
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
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.EditOutcome;
import ua.bookloom.pipeline.reviewer.EditVerifier;
import ua.bookloom.pipeline.reviewer.ReviewItem;
import ua.bookloom.pipeline.reviewer.ReviewPass;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewVerdict;
import ua.bookloom.pipeline.reviewer.ReviewedPair;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/**
 * Sends every case through the production prompt builders — {@link DraftPromptBuilder}, {@link DirectedFix} and
 * {@link ReviewerCall} — to a real model, and measures each reply before any repair the run would make. A reviewer
 * reply is measured the way the run uses it: its edits go through the production {@link EditApplier}.
 */
@Slf4j
final class PromptEvalRunner {

    private final ModelCalls calls;
    private final CallFrame frame;
    private final PromptTemplates templates = new PromptTemplates();
    private final DraftReplyParser parser = new DraftReplyParser(new ObjectMapper());
    private final DraftPromptBuilder builder;
    private final DirectedFix directedFix = new DirectedFix(templates, parser);
    private final ReviewerCall reviewerCall = new ReviewerCall(templates, new ReviewReplyParser(new ObjectMapper()));
    private final EditApplier editApplier = new EditApplier(new EditVerifier());

    PromptEvalRunner(final ModelCalls calls) {
        this(calls, PromptEvalCases.SOURCE_LANGUAGE, PromptEvalCases.TARGET_LANGUAGE);
    }

    /** A runner for one language pair; the language corpora run one of these per target language. */
    PromptEvalRunner(final ModelCalls calls, final String sourceLanguage, final String targetLanguage) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.frame = new CallFrame(
                sourceLanguage,
                targetLanguage,
                StyleSheet.from(BookBrief.defaults(sourceLanguage)),
                ForeignPassagePolicy.KEEP);
        this.builder = new DraftPromptBuilder(templates, frame);
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
        final Checked checked = checkedBy(defect);
        if (checked.refused()) {
            // The run never shows a candidate a check refuses to the reviewer, so asking would measure nothing.
            return new DefectRow(
                    defect.id(), defect.kind(), defect.defective(), true, true, true, Math.max(1, repeats), 0, false);
        }
        final List<Reviewed> reviews = java.util.stream.IntStream.range(0, Math.max(1, repeats))
                .mapToObj(run -> reviewOne(defect.source(), defect.candidate()))
                .toList();
        final boolean byReviewer = reviews.get(0).flagged();
        return new DefectRow(
                defect.id(),
                defect.kind(),
                defect.defective(),
                byReviewer || checked.noted(),
                reviews.stream().allMatch(Reviewed::readable),
                reviews.stream()
                        .allMatch(review ->
                                review.signature().equals(reviews.get(0).signature())),
                reviews.size(),
                reviews.stream().mapToInt(Reviewed::tokenBreaks).sum(),
                byReviewer);
    }

    /** What the run's own checks say about a candidate before any reviewer sees it. */
    private record Checked(boolean refused, boolean noted) {}

    private Checked checkedBy(final DefectCase defect) {
        final var drafted = new DraftOutcome.Drafted(
                segment(defect.source()),
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
                NamePolicy.TRANSLITERATE,
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

    /**
     * What one reviewer call came to once its edits went through the verifier.
     *
     * @param readable whether the reply parsed
     * @param flagged whether the reviewer asked for a change the app would act on: an applied edit, an edit whose quote
     *     is in the text but was refused, or a rewrite
     * @param tokenBreaks how many applied changes altered the candidate's placeholder tokens
     * @param signature the change asked for, so a repeated run can be compared with it
     * @param detail what the reviewer asked for, for the report
     */
    private record Reviewed(boolean readable, boolean flagged, int tokenBreaks, String signature, String detail) {}

    private List<EvalRow> run(final EvalCase evalCase) {
        log.info("Prompt eval case {}", evalCase.name());
        return switch (evalCase) {
            case Draft draft -> List.of(draft(draft));
            case Fix fix -> List.of(fix(fix));
            case Review review -> List.of(review(review));
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

    private EvalRow review(final Review review) {
        final Reviewed good = reviewOne(review.masked(), review.good());
        final Reviewed bad = reviewOne(review.masked(), review.bad());
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

    private Reviewed reviewOne(final String masked, final String candidate) {
        final Result<ReviewVerdict> result = reviewerCall.review(
                List.of(new ReviewedPair("Eval:0", masked, candidate)), frame, List.of(), ReviewPass.FIRST, calls);
        final ReviewVerdict verdict =
                result.isOk() ? Objects.requireNonNull(result.data()) : ReviewVerdict.unreadable();
        if (!verdict.readable()) {
            return new Reviewed(false, !isClean(candidate), 0, "unreadable", "unreadable");
        }
        final ReviewItem item = verdict.itemFor("Eval:0").orElseGet(() -> ReviewItem.ok("Eval:0"));
        return switch (item.status()) {
            case OK -> new Reviewed(true, false, 0, "ok", "ok");
            case EDITS -> editsOf(masked, candidate, item);
            case REWRITE -> rewriteOf(candidate, item);
        };
    }

    private Reviewed editsOf(final String masked, final String candidate, final ReviewItem item) {
        final EditOutcome outcome = editApplier.apply(
                candidate,
                item.edits(),
                text -> blockersOf(masked, text),
                blockersOf(masked, candidate).orElseGet(java.util.Set::of));
        final boolean flagged =
                !outcome.applied().isEmpty() || !outcome.failed().isEmpty();
        final int broken = Tokens.inOrder(outcome.text()).equals(Tokens.inOrder(candidate)) ? 0 : 1;
        final String detail = "edits applied=" + outcome.applied().size() + " refused="
                + outcome.failed().size() + " ignored=" + outcome.ignored() + " notes="
                + outcome.notes().size();
        return new Reviewed(
                true,
                flagged,
                broken,
                "edits:" + outcome.text() + outcome.failed().size(),
                detail);
    }

    private Reviewed rewriteOf(final String candidate, final ReviewItem item) {
        final String rewrite = Objects.requireNonNull(item.rewrite());
        final int broken = Tokens.inOrder(rewrite).equals(Tokens.inOrder(candidate)) ? 0 : 1;
        return new Reviewed(
                true, true, 0, signature(item), "rewrite" + (broken == 0 ? "" : " (breaks tokens, refused)"));
    }

    private static String signature(final ReviewItem item) {
        return item.status() + item.rewrite();
    }

    private java.util.Optional<java.util.Set<String>> blockersOf(final String masked, final String text) {
        return java.util.Optional.of(
                TextChecks.run(
                                DisplayText.of(masked),
                                DisplayText.of(text),
                                frame.sourceLanguage(),
                                frame.targetLanguage())
                        .stream()
                        .filter(CheckFinding::blocking)
                        .map(finding -> finding.kind().name())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    /** Whether a candidate carries no blocking defect of its own, so an unreadable reply on it is not a miss. */
    private boolean isClean(final String candidate) {
        return candidate.isBlank();
    }

    private EvalRow measured(
            final String name, final String kind, final String masked, final String target, final Expect expect) {
        return new EvalRow(
                name,
                kind,
                Check.PASS,
                ReplyChecks.gate(masked, target),
                ReplyChecks.script(masked, target, expect, frame.targetLanguage()),
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
