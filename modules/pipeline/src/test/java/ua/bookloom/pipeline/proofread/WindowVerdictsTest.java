package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.proofread.ProofreadFixtures.PROJECT;
import static ua.bookloom.pipeline.proofread.ProofreadFixtures.decided;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
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
import ua.bookloom.pipeline.TestBooks;
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

    // The refusal's words are book text: above TRACE only the veto's name is logged.
    @Test
    void rewrite_refusedForAWord_logsTheWordAtTraceOnly() {
        final Logger logger = (Logger) LoggerFactory.getLogger("ua.bookloom.pipeline.proofread");
        final Level previous = logger.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
        try {
            decide(
                    List.of(),
                    "Мирена кивнула и сказала, что ключ у Тарна.",
                    decided(ID, SegmentStatus.ACCEPTED, "Мирена кивнула й промовила, що ключ має Тарн."),
                    "Мирена кивнула й промовила, что ключ має Тарн.");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.DEBUG))
                .noneMatch(event -> event.getFormattedMessage().contains("что"));
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.TRACE)
                .anyMatch(event -> event.getFormattedMessage().contains("что"));
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

    // Max's second round proposes again for the same edited segment: it supersedes round 1's committed proposal.
    @Test
    void rewrite_secondProposalAfterACommittedOne_resolvesTheFirst() {
        final String edit = "Він рвучко відчинив двері, а потім ещё раз.";
        final SegmentRecord edited = decided(ID, SegmentStatus.REVISED, "Він відчинив двері, а потім ещё раз.")
                .withUserTarget(edit, edit);
        final Segment segment = ProofreadFixtures.chapter("ch02.md", "Он рывком открыл дверь, а потом ещё раз.")
                .units()
                .getFirst()
                .segments()
                .getFirst();
        final WindowVerdicts verdicts = verdicts(List.of(), List.of());
        final WindowVerdicts.Decided first = verdicts.decide(
                List.of(read(segment, edited)), Map.of(ID, "Він рвучко відчинив двері, а потім ще раз."), "r1:a");
        verdicts.committed(first);

        final WindowVerdicts.Decided second = verdicts.decide(
                List.of(read(segment, edited)), Map.of(ID, "Він різко відчинив двері, а потім ще раз."), "r2:b");

        assertThat(second.resolved())
                .containsExactly(first.proposals().getFirst().id());
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

    private static final GlossaryEntry NEWTON =
            GlossaryEntry.of("p:Ньютон", PROJECT, "Ньютон", "Ньютон", TermType.CHARACTER, Gender.FEMALE, false);
    private static final GlossaryEntry EINSTEIN = GlossaryEntry.of(
            "p:Эйнштейн", PROJECT, "Альберт Эйнштейн", "Альберт Ейнштейн", TermType.CHARACTER, Gender.MALE, false);
    private static final String GRAVITY_SOURCE =
            "Ньютон опубликовал закон тяготения, а Альберт Эйнштейн опубликовал теорию, где пространство искривлено.";
    private static final String GRAVITY_CURRENT =
            "Ньютон опублікував закон тяжіння, а Альберт Ейнштейн опублікував теорію, де простір викривлений.";

    // The hand check's case: a mark for Newton's gender alone may change Newton's verb, not Einstein's verb or another
    // word, so a rewrite that also makes Einstein a woman and adds a word is refused and the text stays.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Ньютон опублікувала закон тяжіння, а Альберт Ейнштейн опублікувала теорію, де космічний простір"
                        + " викривлений.",
                "Ньютон опублікувала закон тяжіння, а Альберт Ейнштейн опублікувала теорію, де простір викривлений.",
                "Ньютон опублікувала закон тяжіння, а Альберт Ейнштейн опублікував теорію, де космічний простір"
                        + " викривлений."
            })
    void rewrite_genderMarkChangesOtherWords_rejectedAndTextStays(final String rewrite) {
        final WindowVerdicts.Decided decided = decideGravity(rewrite);

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo(GRAVITY_CURRENT);
        assertThat(record.proofread()).extracting(ProofreadVerdict::outcome).isEqualTo(ProofreadOutcome.REJECTED);
        assertThat(Objects.requireNonNull(record.proofread()).reason()).startsWith("gender-scope");
        assertThat(decided.resolved()).isEmpty();
    }

    @Test
    void rewrite_genderMarkChangesOnlyTheWrongWord_keptAndDeferralSettled() {
        final String rewrite =
                "Ньютон опублікувала закон тяжіння, а Альберт Ейнштейн опублікував теорію, де простір викривлений.";

        final WindowVerdicts.Decided decided = decideGravity(rewrite);

        assertThat(decided.records().getFirst().machineTarget()).isEqualTo(rewrite);
        assertThat(decided.resolved()).containsExactly("d-newton");
    }

    // The same gender-only fix in an EPUB paragraph whose name is in italics: the markup is no word of the rewrite.
    @Test
    void rewrite_genderMarkOnAFormattedSegment_keptAndDeferralSettled(@TempDir final Path directory) {
        final Segment segment = Objects.requireNonNull(TestDocuments.documents()
                        .open(TestBooks.epub(
                                directory.resolve("book.epub"),
                                List.of(List.of("<em>Ньютон</em> опубликовал закон тяготения, а Альберт Эйнштейн"
                                        + " опубликовал теорию, где пространство искривлено.")),
                                "ru"))
                        .data())
                .units()
                .stream()
                .flatMap(unit -> unit.segments().stream())
                .findFirst()
                .orElseThrow();
        final String current = "⟦g0⟧Ньютон⟦g1⟧ опублікував закон тяжіння, а Альберт Ейнштейн опублікував теорію, де"
                + " простір викривлений.";
        final String rewrite = current.replaceFirst("опублікував", "опублікувала");
        final SegmentRecord record = decided(segment.id(), SegmentStatus.ACCEPTED, current);
        final Deferral waiting = new Deferral(
                "d-newton", PROJECT, segment.id(), DeferralReason.GENDER_CHANGED, "Ньютон", null, null, null);

        final WindowVerdicts.Decided decided = verdicts(List.of(NEWTON, EINSTEIN), List.of(waiting))
                .decide(
                        List.of(new WindowVerdicts.Read(
                                segment, record, current, List.of("gender now known: Ньютон — female"))),
                        Map.of(segment.id(), rewrite),
                        PRINT);

        assertThat(decided.records().getFirst().maskedMachineTarget()).isEqualTo(rewrite);
        assertThat(decided.resolved()).containsExactly("d-newton");
    }

    private static WindowVerdicts.Decided decideGravity(final String rewrite) {
        final Deferral waiting =
                new Deferral("d-newton", PROJECT, ID, DeferralReason.GENDER_CHANGED, "Ньютон", null, null, null);
        final Document document = ProofreadFixtures.chapter("ch02.md", GRAVITY_SOURCE);
        final SegmentRecord record = decided(ID, SegmentStatus.ACCEPTED, GRAVITY_CURRENT);
        return verdicts(List.of(NEWTON, EINSTEIN), List.of(waiting))
                .decide(
                        List.of(new WindowVerdicts.Read(
                                document.units().getFirst().segments().getFirst(),
                                record,
                                Objects.requireNonNull(MarkList.maskedTarget(record)),
                                List.of("gender now known: Ньютон — female"))),
                        Map.of(ID, rewrite),
                        PRINT);
    }

    // Max's whole-book round reads a segment whose first-round rewrite was kept and changes nothing: the kept verdict
    // and its text before stay, under the new fingerprint, so Undo proofread still has the original.
    @Test
    void reply_unchangedAfterKeptRewrite_keepsTheFirstTextBefore() {
        final Document document = ProofreadFixtures.chapter("ch02.md", NEGATION_SOURCE);
        final SegmentRecord kept = decided(ID, SegmentStatus.REVISED, NEGATION_KEPT)
                .withProofread(new ProofreadVerdict(
                        "r1:first",
                        ProofreadOutcome.KEPT,
                        "",
                        NEGATION_LOST,
                        SegmentStatus.ACCEPTED,
                        NEGATION_LOST,
                        List.of()));

        final WindowVerdicts.Decided decided = verdicts(List.of(ZOTOV), List.of())
                .decide(List.of(read(document.units().getFirst().segments().getFirst(), kept)), Map.of(), "r2:whole");

        assertThat(decided.records().getFirst().proofread())
                .extracting(
                        ProofreadVerdict::fingerprint,
                        ProofreadVerdict::outcome,
                        ProofreadVerdict::before,
                        ProofreadVerdict::statusBefore)
                .containsExactly("r2:whole", ProofreadOutcome.KEPT, NEGATION_LOST, SegmentStatus.ACCEPTED);
        assertThat(decided.counts()).containsExactly(Map.entry(ProofreadOutcome.UNCHANGED, 1));
    }

    private static final String IVAR_SOURCE = "Ивар сказал, что вернётся.";
    private static final String IVAR_CURRENT = "Івар сказав, що повернеться.";
    private static final String IVAR_REWRITTEN = "Івар сказала, що повернеться.";

    private static WindowVerdicts.Decided decideIvar(final GlossaryEntry ivar) {
        final Deferral waiting =
                new Deferral("d1", PROJECT, ID, DeferralReason.GENDER_CHANGED, "Ивар", null, null, null);
        final Document document = ProofreadFixtures.chapter("ch02.md", IVAR_SOURCE);
        return verdicts(List.of(ivar), List.of(waiting))
                .decide(
                        List.of(read(
                                document.units().getFirst().segments().getFirst(),
                                decided(ID, SegmentStatus.ACCEPTED, IVAR_CURRENT))),
                        Map.of(ID, IVAR_REWRITTEN),
                        PRINT);
    }

    private static GlossaryEntry ivarUnknown() {
        return GlossaryEntry.of("p:Ивар", PROJECT, "Ивар", "Івар", TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    // A gender the book verified from its pronouns is acted on like one the person set.
    @Test
    void rewrite_genderInferredFromTheBook_keptAndDeferralSettled() {
        final WindowVerdicts.Decided decided = decideIvar(ivarUnknown().withInferredGender(Gender.FEMALE));

        assertThat(decided.records().getFirst().machineTarget()).isEqualTo(IVAR_REWRITTEN);
        assertThat(decided.resolved()).containsExactly("d1");
    }

    // IF a suggested gender were settled by a rewrite, THEN one wrong guess would close the deferral for good.
    @Test
    void rewrite_genderOnlySuggested_settlesNoDeferral() {
        final WindowVerdicts.Decided decided = decideIvar(ivarUnknown().withSuggestedGender(Gender.FEMALE));

        assertThat(decided.resolved()).isEmpty();
    }

    private static final String DOOR_SOURCE = "Он открыл <em>старую</em> дверь.";
    private static final String DOOR_CURRENT = "Він відчинив ⟦g0⟧стару⟦g1⟧ двері.";

    // A rewrite that drops a placeholder fails the document gate, so the text stays.
    @Test
    void rewrite_dropsPlaceholder_rejectedAndTextStays(@TempDir final Path directory) {
        final WindowVerdicts.Decided decided = decideFormatted(directory, "Він відчинила ⟦g0⟧стару двері.");

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.maskedMachineTarget()).isEqualTo(DOOR_CURRENT);
        assertThat(record.proofread()).extracting(ProofreadVerdict::outcome).isEqualTo(ProofreadOutcome.REJECTED);
    }

    // A rewrite that echoes the source in the source language is no translation.
    @Test
    void rewrite_echoesSource_rejectedAndTextStays() {
        final WindowVerdicts.Decided decided = decide(
                List.of(),
                "Он купил кобылу.",
                decided(ID, SegmentStatus.FLAGGED, "Він купив кобылу."),
                "Он купил кобылу.");

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo("Він купив кобылу.");
        assertThat(record.proofread()).extracting(ProofreadVerdict::outcome).isEqualTo(ProofreadOutcome.REJECTED);
    }

    // A blank rewrite changes nothing and is no kept text.
    @Test
    void rewrite_emptyText_textStaysAndIsNotKept() {
        final WindowVerdicts.Decided decided =
                decide(List.of(), "Он купил кобылу.", decided(ID, SegmentStatus.FLAGGED, "Він купив кобылу."), "");

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo("Він купив кобылу.");
        assertThat(Objects.requireNonNull(record.proofread()).outcome()).isNotEqualTo(ProofreadOutcome.KEPT);
    }

    // "Nothing to change" is an answer, not a refusal: the text stays and the verdict is UNCHANGED.
    @Test
    void reply_noChangeForTheFocus_unchangedNotRejected() {
        final WindowVerdicts.Decided decided = verdicts(List.of(), List.of())
                .decide(
                        List.of(read(
                                ProofreadFixtures.chapter("ch02.md", "Он ушёл.")
                                        .units()
                                        .getFirst()
                                        .segments()
                                        .getFirst(),
                                decided(ID, SegmentStatus.FLAGGED, "Він пішов."))),
                        Map.of(),
                        PRINT);

        assertThat(decided.counts()).containsExactly(Map.entry(ProofreadOutcome.UNCHANGED, 1));
        assertThat(decided.records().getFirst().machineTarget()).isEqualTo("Він пішов.");
    }

    // IF the codes were refused, THEN the fix of a dialogue paragraph would be wasted: quote marks written as control
    // codes are put back where a quote belongs.
    @Test
    void rewrite_quoteCodes_mappedAndKept() {
        final WindowVerdicts.Decided decided = decide(
                List.of(),
                "«Он купил кобылу.»",
                decided(ID, SegmentStatus.FLAGGED, "«Він купив кобылу.»"),
                "\u001eВін купив кобилу.\u001d");

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo("«Він купив кобилу.»");
        assertThat(record.proofread()).extracting(ProofreadVerdict::outcome).isEqualTo(ProofreadOutcome.KEPT);
    }

    // A code that has no clear place, or quotes that do not pair up, are refused naming the control codes.
    @ParameterizedTest
    @ValueSource(strings = {"Він\u001e купив кобилу.", "\u001eВін купив кобилу."})
    void rewrite_codeWithNoPlace_rejectedNamingTheControlCodes(final String rewrite) {
        final WindowVerdicts.Decided decided = decide(
                List.of(), "«Он купил кобылу.»", decided(ID, SegmentStatus.FLAGGED, "«Він купив кобылу.»"), rewrite);

        final SegmentRecord record = decided.records().getFirst();
        assertThat(record.machineTarget()).isEqualTo("«Він купив кобылу.»");
        assertThat(record.proofread()).extracting(ProofreadVerdict::outcome).isEqualTo(ProofreadOutcome.REJECTED);
        assertThat(Objects.requireNonNull(record.proofread()).reason()).contains("control");
    }

    private static WindowVerdicts.Decided decideFormatted(final Path directory, final String rewrite) {
        final Segment segment = Objects.requireNonNull(TestDocuments.documents()
                        .open(TestBooks.epub(directory.resolve("book.epub"), List.of(List.of(DOOR_SOURCE)), "ru"))
                        .data())
                .units()
                .stream()
                .flatMap(unit -> unit.segments().stream())
                .findFirst()
                .orElseThrow();
        final SegmentRecord record = decided(segment.id(), SegmentStatus.FLAGGED, DOOR_CURRENT);
        return verdicts(List.of(), List.of())
                .decide(List.of(read(segment, record)), Map.of(segment.id(), rewrite), PRINT);
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
