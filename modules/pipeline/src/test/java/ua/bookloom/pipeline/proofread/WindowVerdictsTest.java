package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.proofread.ProofreadFixtures.PROJECT;
import static ua.bookloom.pipeline.proofread.ProofreadFixtures.decided;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.typography.TypographyGate;

/**
 * What a proofread window's answer does to each segment it read (design D14, D15 "Reply"), through the real document
 * gate, the real checks and the real never-worse gate. Invented ru→uk lines.
 */
class WindowVerdictsTest {

    private static final BookBrief BRIEF = ProofreadFixtures.brief("ru", "uk");
    private static final String PRINT = "fingerprint";
    private static final String ID = "ch02.md:0";

    private static final GlossaryEntry ZOTOV =
            GlossaryEntry.of("p:Зотов", PROJECT, "Зотов", "Зотов", TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry IVAR =
            GlossaryEntry.of("p:Ивар", PROJECT, "Ивар", "Івар", TermType.CHARACTER, Gender.FEMALE, false);

    private static final String NEGATION_SOURCE = "Я дошла до Зотова только к ночи и никого не встретила на тропе.";
    private static final String NEGATION_LOST = "Я дійшла до Зотова лише під ніч і зустріла на стежці.";
    private static final String NEGATION_KEPT = "Я дійшла до Зотова лише під ніч і нікого не зустріла на стежці.";

    @Test
    void rewrite_negationRestored_keptRevisedBeforeStored() {
        final WindowVerdicts.Decided decided = decide(
                List.of(ZOTOV), NEGATION_SOURCE, decided(ID, SegmentStatus.ACCEPTED, NEGATION_LOST), NEGATION_KEPT);

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo(NEGATION_KEPT);
        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.proofread())
                .extracting(ProofreadVerdict::outcome, ProofreadVerdict::before, ProofreadVerdict::statusBefore)
                .containsExactly(ProofreadOutcome.KEPT, NEGATION_LOST, SegmentStatus.ACCEPTED);
    }

    @Test
    void rewrite_foreignNameForm_rejectedNamingTheForm() {
        final WindowVerdicts.Decided decided = decide(
                List.of(ZOTOV),
                NEGATION_SOURCE,
                decided(ID, SegmentStatus.ACCEPTED, NEGATION_KEPT),
                "Я дійшла до Зотової лише під ніч і нікого не зустріла на стежці.");

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo(NEGATION_KEPT);
        assertThat(record.proofread()).extracting(ProofreadVerdict::outcome).isEqualTo(ProofreadOutcome.REJECTED);
        assertThat(Objects.requireNonNull(record.proofread()).reason()).contains("Зотової is not a form of Зотов");
    }

    // The proofread trap of mini-ru: a rewrite that brings in the Russian function word is turned down by name.
    @Test
    void rewrite_sourceOnlyFunctionWord_rejectedNamingTheWord() {
        final WindowVerdicts.Decided decided = decide(
                List.of(),
                "Мирена кивнула и сказала, что ключ у Тарна.",
                decided(ID, SegmentStatus.ACCEPTED, "Мирена кивнула й промовила, що ключ має Тарн."),
                "Мирена кивнула й промовила, что ключ має Тарн.");

        final ProofreadVerdict verdict =
                Objects.requireNonNull(decided.records().getFirst().proofread());
        assertThat(verdict.outcome()).isEqualTo(ProofreadOutcome.REJECTED);
        assertThat(verdict.reason()).contains("что");
    }

    @Test
    void rewrite_personEdit_proposalAndTheEditStays() {
        final String edit = "Він рвучко відчинив двері, а потім ещё раз.";
        final String rewrite = "Він рвучко відчинив двері, а потім ще раз.";
        final SegmentRecord edited = decided(ID, SegmentStatus.REVISED, "Він відчинив двері, а потім ещё раз.")
                .withUserTarget(edit, edit);

        final WindowVerdicts.Decided decided =
                decide(List.of(), "Он рывком открыл дверь, а потом ещё раз.", edited, rewrite);

        assertThat(decided.records().getFirst().maskedUserTarget()).isEqualTo(edit);
        assertThat(decided.records().getFirst().proofread())
                .extracting(ProofreadVerdict::outcome)
                .isEqualTo(ProofreadOutcome.PROPOSED);
        assertThat(decided.proposals())
                .extracting(Deferral::reason, Deferral::maskedProposal)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(DeferralReason.PROOFREAD, rewrite));
    }

    @Test
    void rewrite_flaggedSegmentClearedOfItsBlocker_revised() {
        final WindowVerdicts.Decided decided = decide(
                List.of(),
                "Он купил кобылу.",
                decided(ID, SegmentStatus.FLAGGED, "Він купив кобылу."),
                "Він купив кобилу.");

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo("Він купив кобилу.");
        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.proofread())
                .extracting(ProofreadVerdict::outcome, ProofreadVerdict::statusBefore)
                .containsExactly(ProofreadOutcome.KEPT, SegmentStatus.FLAGGED);
    }

    // The rewrite clears the Russian letter but still drops the second sentence, which blocks: it waits on the desk.
    @Test
    void rewrite_flaggedSegmentStillBlocked_staysFlaggedWithProposal() {
        final WindowVerdicts.Decided decided = decide(
                List.of(),
                "Он купил кобылу у соседа на рынке. Потом он долго шёл домой через поле.",
                decided(ID, SegmentStatus.FLAGGED, "Він купив кобылу в сусіда на ринку."),
                "Він купив кобилу в сусіда на ринку.");

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo("Він купив кобылу в сусіда на ринку.");
        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.proofread()).extracting(ProofreadVerdict::outcome).isEqualTo(ProofreadOutcome.PROPOSED);
        assertThat(decided.proposals())
                .extracting(Deferral::maskedProposal)
                .containsExactly("Він купив кобилу в сусіда на ринку.");
    }

    @Test
    void reply_missingFocusId_unchanged() {
        final WindowVerdicts verdicts = verdicts(List.of(), List.of());
        final Document document = ProofreadFixtures.chapter("ch02.md", "Он купил кобылу.", "Он ушёл.");
        final Segment first = document.units().getFirst().segments().get(0);
        final Segment second = document.units().getFirst().segments().get(1);

        final WindowVerdicts.Decided decided = verdicts.decide(
                List.of(
                        read(first, decided("ch02.md:0", SegmentStatus.FLAGGED, "Він купив кобылу.")),
                        read(second, decided("ch02.md:1", SegmentStatus.ACCEPTED, "Він пішов."))),
                Map.of("ch02.md:0", "Він купив кобилу."),
                PRINT);

        assertThat(decided.records())
                .extracting(record -> Objects.requireNonNull(record.proofread()).outcome())
                .containsExactly(ProofreadOutcome.KEPT, ProofreadOutcome.UNCHANGED);
        assertThat(decided.records().get(1).machineTarget()).isEqualTo("Він пішов.");
    }

    // A gender the person set after the run: the rewrite agrees with it, so its waiting deferral is settled with it.
    @Test
    void rewrite_genderNowKnown_keptAndDeferralSettled() {
        final Deferral waiting =
                new Deferral("d1", PROJECT, ID, DeferralReason.GENDER_CHANGED, "Ивар", null, null, null);
        final Document document = ProofreadFixtures.chapter("ch02.md", "Ивар сказал, что вернётся.");

        final WindowVerdicts.Decided decided = verdicts(List.of(IVAR), List.of(waiting))
                .decide(
                        List.of(read(
                                document.units().getFirst().segments().getFirst(),
                                decided(ID, SegmentStatus.ACCEPTED, "Івар сказав, що повернеться."))),
                        Map.of(ID, "Івар сказала, що повернеться."),
                        PRINT);

        assertThat(decided.records().getFirst().machineTarget()).isEqualTo("Івар сказала, що повернеться.");
        assertThat(decided.resolved()).containsExactly("d1");
    }

    private static WindowVerdicts.Decided decide(
            final List<GlossaryEntry> glossary, final String source, final SegmentRecord record, final String rewrite) {
        final Document document = ProofreadFixtures.chapter("ch02.md", source);
        return verdicts(glossary, List.of())
                .decide(
                        List.of(read(document.units().getFirst().segments().getFirst(), record)),
                        Map.of(ID, rewrite),
                        PRINT);
    }

    private static WindowVerdicts.Read read(final Segment segment, final SegmentRecord record) {
        return new WindowVerdicts.Read(
                segment, record, Objects.requireNonNull(MarkList.maskedTarget(record)), List.of("flagged: alphabet"));
    }

    private static WindowVerdicts verdicts(final List<GlossaryEntry> glossary, final List<Deferral> open) {
        final Document document = ProofreadFixtures.chapter("ch02.md", "Строка.");
        final GateFunction gate =
                TypographyGate.around(GateFunction.of(TestDocuments.documents(), BookFormat.EPUB), "uk");
        final ProofreadJudge judge = new ProofreadJudge(new ProofreadJudge.Book(
                ProofreadFixtures.frame(BRIEF),
                NamePolicy.TRANSLITERATE,
                glossary,
                new FinalAudit.Book(BRIEF, document, glossary, WordValidator.none()),
                gate));
        return new WindowVerdicts(
                judge, new WindowVerdicts.Facts(PROJECT, "uk", MarkList.byTerm(glossary), BRIEF.narrator(), open));
    }
}
