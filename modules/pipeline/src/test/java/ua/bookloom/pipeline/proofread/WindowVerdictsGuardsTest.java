package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.proofread.ProofreadFixtures.PROJECT;
import static ua.bookloom.pipeline.proofread.ProofreadFixtures.decided;
import static ua.bookloom.pipeline.proofread.WindowVerdictsTest.ID;
import static ua.bookloom.pipeline.proofread.WindowVerdictsTest.PRINT;
import static ua.bookloom.pipeline.proofread.WindowVerdictsTest.decide;
import static ua.bookloom.pipeline.proofread.WindowVerdictsTest.read;
import static ua.bookloom.pipeline.proofread.WindowVerdictsTest.verdicts;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestDocuments;

/**
 * The cases the backward revision's tests held for a model's answer — the gender a rewrite is settled by, a dropped
 * placeholder, an echo, an empty or unchanged reply, and quote marks written as control codes — read through the
 * proofread window's verdicts. Invented ru→uk lines.
 */
class WindowVerdictsGuardsTest {

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
}
