package ua.bookloom.pipeline.proofread;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ControlCharacterMapper;
import ua.bookloom.pipeline.ControlCharacters;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.TextSpan;
import ua.bookloom.pipeline.context.CharacterSheet;
import ua.bookloom.pipeline.glossary.GlossaryNames;
import ua.bookloom.pipeline.heal.DraftSubject;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.heal.QaEvaluation;
import ua.bookloom.pipeline.heal.SoftContext;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.qa.Candidate;
import ua.bookloom.pipeline.qa.NeverWorse;
import ua.bookloom.pipeline.qa.NeverWorseReasons;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.qa.Verdict;
import ua.bookloom.pipeline.qa.WindowFacts;

/**
 * Holds one proofread rewrite to the hard gates and the never-worse gate (design D14, D15 "Reply"): the rewrite is
 * restored through the document's placeholder gate, both texts are evaluated by the same checks with the book's
 * glossary and character genders as they are now, and the audit's doubts count too. A refusal names the veto and the
 * word that broke it.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ProofreadJudge {

    private static final String GENDER_KIND = "gender";
    private static final String GENDER_DEFERRAL = "gender-deferral";
    private static final String VETO_WORDS = ": ";
    private static final String CONTROL_CHARACTERS = "control-characters";

    private final Book book;

    /**
     * What the judge reads of the book.
     *
     * @param frame the run's languages and policies
     * @param names the brief's name policy
     * @param glossary the glossary as the stage read it
     * @param audit the brief, the opened book and the glossary as the final audit reads them
     * @param gate the document's placeholder gate, around the run's typography
     */
    record Book(
            CallFrame frame, NamePolicy names, List<GlossaryEntry> glossary, FinalAudit.Book audit, GateFunction gate) {

        /** Rejects a missing part and copies the glossary. */
        Book {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(names, "names");
            glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
            Objects.requireNonNull(audit, "audit");
            Objects.requireNonNull(gate, "gate");
        }
    }

    /** What the gates made of a rewrite. */
    sealed interface Judged {

        /**
         * The rewrite may replace the text.
         *
         * @param restored the rewrite restored through the gate, in both forms
         * @param qa the rewrite's evaluation
         */
        record Keep(GateResult.Restored restored, QaResult qa) implements Judged {}

        /**
         * The rewrite is refused.
         *
         * @param reason the gate's reason in words
         */
        record Reject(String reason) implements Judged {}
    }

    ProofreadJudge(final Book book) {
        this.book = Objects.requireNonNull(book, "book");
    }

    /**
     * Judges one rewrite.
     *
     * @param segment the non-null opened book's segment
     * @param current the non-null text in place, document tokens in place
     * @param rewrite the non-null text the reply gave
     * @param reasons the non-null reasons the segment was marked by, which name the finding a keep should clear
     * @param arrived the non-null genders now known that the segment's open deferrals waited on
     * @return the judgement; never null
     */
    Judged judge(
            final Segment segment,
            final String current,
            final String rewrite,
            final List<String> reasons,
            final List<ArrivedGender> arrived) {
        final Optional<String> readable = withoutControlCodes(segment, rewrite);
        if (readable.isEmpty()) {
            return rejected(segment, CONTROL_CHARACTERS);
        }
        final String candidate = WhitespaceRestoration.restore(current, readable.get());
        return switch (book.gate().restore(segment, candidate)) {
            case GateResult.Restored restored -> inScope(segment, new Texts(current, restored), reasons, arrived);
            case GateResult.GateFailed failed ->
                rejected(segment, failed.finding().raisedBy());
            case GateResult.StepError(final AppError error) -> rejected(segment, "gate-error " + error.code());
        };
    }

    // A model that copies its quote marks and dashes as control codes is read the way a draft's reply is: each code
    // goes back to the mark it stands for when its place says which, else the rewrite is refused by name. The edge is
    // stripped without String.strip(), which would take a code at the edge for spacing and lose an opening quote.
    private Optional<String> withoutControlCodes(final Segment segment, final String rewrite) {
        final String stripped = ControlCharacters.stripSpacing(rewrite);
        if (!ControlCharacters.addsControl(segment.masked(), stripped)) {
            return Optional.of(stripped);
        }
        log.debug("Proofread rewrite holds control codes segmentId={}", segment.id());
        return ControlCharacterMapper.map(
                segment.masked(), stripped, book.frame().targetLanguage());
    }

    /** The text in place and the rewrite as the gate restored it. */
    private record Texts(String current, GateResult.Restored restored) {

        // Read on the masked form like the text in place, so the book's markup and escapes are no words.
        String plainAfter() {
            return DisplayText.of(restored.maskedForm());
        }
    }

    // A mark only a gender now known stands behind is answered by its wrong words alone (GenderScope).
    private Judged inScope(
            final Segment segment, final Texts texts, final List<String> reasons, final List<ArrivedGender> arrived) {
        if (!GenderScope.holds(reasons)) {
            return byNeverWorse(segment, texts, reasons, arrived);
        }
        final String plainBefore = DisplayText.of(texts.current());
        final List<TextSpan> wrong = arrived.stream()
                .flatMap(fact -> fact.wrongWords(plainBefore, book.frame().targetLanguage()).stream())
                .toList();
        return GenderScope.breach(plainBefore, texts.plainAfter(), wrong)
                .map(reason -> rejected(segment, reason))
                .orElseGet(() -> byNeverWorse(segment, texts, reasons, arrived));
    }

    private Judged byNeverWorse(
            final Segment segment, final Texts texts, final List<String> reasons, final List<ArrivedGender> arrived) {
        final String current = texts.current();
        final GateResult.Restored restored = texts.restored();
        final QaResult before = evaluate(segment, current, current);
        final QaResult after = evaluate(segment, restored.candidateOr(restored.maskedForm()), restored.maskedForm());
        final WindowFacts facts = WindowFacts.of(
                segment.masked(),
                book.frame().sourceLanguage(),
                book.frame().targetLanguage(),
                book.glossary(),
                trigger(before, reasons, arrived, texts),
                false);
        final Candidate held = new Candidate(current, before, FinalAudit.checksOf(book.audit(), segment, current));
        final Candidate fresh = new Candidate(
                restored.maskedForm(), after, FinalAudit.checksOf(book.audit(), segment, restored.maskedForm()));
        final Verdict verdict = NeverWorse.judge(held, fresh, facts);
        log.debug(
                "Proofread rewrite judged segmentId={} findings={}->{} keep={}",
                segment.id(),
                before.findings().size(),
                after.findings().size(),
                verdict instanceof Verdict.Keep);
        return switch (verdict) {
            case Verdict.Keep() -> new Judged.Keep(restored, after);
            case Verdict.Reject(final String veto) -> rejected(segment, NeverWorseReasons.of(veto, held, fresh, facts));
        };
    }

    private static Judged rejected(final Segment segment, final String reason) {
        log.debug("Proofread rewrite refused segmentId={} veto={}", segment.id(), vetoOf(reason));
        log.trace("Proofread rewrite refused segmentId={} reason={}", segment.id(), reason);
        return new Judged.Reject(reason);
    }

    /**
     * The veto's name of a refusal, without the book's words that follow it, for a log line above TRACE.
     *
     * @param reason the non-null refusal, {@code veto} or {@code veto: words}
     * @return the part before the first {@code ": "}; never null
     */
    static String vetoOf(final String reason) {
        final int end = reason.indexOf(VETO_WORDS);
        return end < 0 ? reason : reason.substring(0, end);
    }

    // The finding a mark named, which a kept rewrite may clear instead of scoring lower; none for a mark no finding
    // stands behind (a gender now known, a detector's doubt).
    // A gender now known that the current text breaks and the rewrite keeps is the mark's own defect, which no check
    // of the run's frame may see (the narrator's gender is inferred after the frame was built), so its fix counts.
    private @Nullable QaFinding trigger(
            final QaResult before, final List<String> reasons, final List<ArrivedGender> arrived, final Texts texts) {
        final String language = book.frame().targetLanguage();
        final String plainBefore = DisplayText.of(texts.current());
        final String plainAfter = texts.plainAfter();
        final @Nullable ArrivedGender fixed = arrived.stream()
                .filter(fact -> fact.mismatches(plainBefore, language) && !fact.mismatches(plainAfter, language))
                .findFirst()
                .orElse(null);
        if (fixed != null) {
            log.debug("Proofread rewrite agrees with a gender now known gender={}", fixed.gender());
            log.trace("Proofread rewrite agrees with a gender now known: {}", fixed.reason());
            return new QaFinding(GENDER_KIND, Severity.LOW, fixed.reason(), GENDER_DEFERRAL);
        }
        return before.findings().stream()
                .filter(finding -> reasons.stream().anyMatch(reason -> reason.contains(finding.raisedBy())))
                .findFirst()
                .orElse(null);
    }

    private QaResult evaluate(final Segment segment, final String candidate, final String maskedForm) {
        final List<GlossaryEntry> glossary = book.glossary();
        return QaEvaluation.evaluate(
                List.of(),
                new DraftSubject(
                        segment,
                        segment.masked(),
                        ProtectedSpans.mask(segment, book.frame(), glossary).presentLocked()),
                candidate,
                maskedForm,
                new SoftContext(
                        book.frame(),
                        book.names(),
                        GlossaryNames.terms(glossary),
                        GlossaryNames.pairs(glossary),
                        CharacterSheet.select(
                                List.of(segment), glossary, book.frame().analysisLanguage()),
                        UnitRole.BODY,
                        GlossaryNames.pinnedTargets(glossary)));
    }
}
