package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_MASKED;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_PLAIN;
import static ua.bookloom.pipeline.revision.RevisionBook.HALE_CAME;
import static ua.bookloom.pipeline.revision.RevisionBook.HALE_LEFT;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_DOOR;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;

/**
 * The consistency pass's deterministic sweep over a real opened EPUB: a locked term the person renamed reaches every
 * segment that used its previous rendering, in both forms and without a model call, and never a rendering the model
 * chose on its own, a term unlocked again, or the person's own edit.
 */
class ConsistencyPassTermTest {

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
    }

    // A locked name the person renamed reaches the segment that used the old rendering, with no model call.
    @Test
    void run_lockedTermRenamed_sweepsBothFormsWithoutCall() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));

        final ConsistencyReport report = ok(book.run(true));

        final SegmentRecord record = book.stored(HALE_LEFT);
        assertThat(record.machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(record.maskedMachineTarget()).isEqualTo("Гейл пішов.");
        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(book.model().requests()).isEmpty();
        assertThat(report).isEqualTo(new ConsistencyReport(1, 0, 0, List.of("ch1 · p04: locked term substituted")));
        assertThat(book.openDeferrals()).isEmpty();
    }

    // The sweep knows only a previous glossary target; a rendering the model chose on its own is never swept.
    @Test
    void run_renderingModelChose_isNotSwept() {
        final GlossaryEntry hale = book.add(book.character("Hale", null, Gender.MALE, false));
        book.decide(HALE_CAME, "Хейл прийшов.", "Хейл прийшов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(HALE_CAME).machineTarget()).isEqualTo("Хейл прийшов.");
        assertThat(book.stored(HALE_CAME).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(report).isEqualTo(new ConsistencyReport(0, 0, 0, List.of()));
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A term the person unlocked again after the change is no longer swept; its deferral is resolved.
    @Test
    void run_termUnlockedAfterChange_resolvesAndKeepsTarget() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        book.glossaryUpdate(book.character("Hale", "Гейл", Gender.MALE, false));

        ok(book.run(true));

        assertThat(book.stored(HALE_LEFT).machineTarget()).isEqualTo("Хейл пішов.");
        assertThat(book.stored(HALE_LEFT).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A target that no longer holds the old rendering has nothing to sweep; its deferral is resolved silently.
    @Test
    void run_targetNoLongerHoldsOldRendering_resolvesAndKeepsTarget() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Він пішов.", "Він пішов.");

        ok(book.run(true));

        assertThat(book.stored(HALE_LEFT).machineTarget()).isEqualTo("Він пішов.");
        assertThat(book.stored(HALE_LEFT).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(book.openDeferrals()).isEmpty();
    }

    // Two changed terms in one segment are both swept into the one stored target.
    @Test
    void run_twoTermsInOneSegment_sweepsBoth() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        final GlossaryEntry went = book.add(term("пішов"));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        book.change(went, term("вийшов"));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(HALE_LEFT).maskedMachineTarget()).isEqualTo("Гейл вийшов.");
        assertThat(report.termSubstitutions()).isEqualTo(2);
        assertThat(report.notes()).containsExactly("ch1 · p04: locked term substituted");
    }

    // A swept term keeps the segment's placeholders: the plain form is restored from the swept masked form.
    @Test
    void run_termInFormattedSegment_restoresPlainFromMasked() {
        final GlossaryEntry sam = book.add(book.character("Sam", "Сем", Gender.MALE, true));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.change(sam, book.character("Sam", "Семюел", Gender.MALE, true));

        ok(book.run(true));

        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo("Семюел відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        assertThat(book.stored(SAM_DOOR).machineTarget()).isEqualTo("Семюел відчинив <em>старі</em> двері.");
    }

    // With no model the deterministic sweep still runs and every gender deferral waits for a pass that has one.
    @Test
    void run_withoutModel_sweepsTermAndKeepsGenderOpen() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.FEMALE, false));

        final ConsistencyReport report = ok(book.run(false));

        assertThat(book.stored(HALE_LEFT).machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_MASKED);
        assertThat(report.termSubstitutions()).isEqualTo(1);
        assertThat(book.openDeferrals()).extracting(Deferral::reason).containsExactly(DeferralReason.GENDER_UNKNOWN);
    }

    // A term swept in a segment the person edited becomes a proposal in both forms; the edit stays as written.
    @Test
    void run_personEditedTermSegment_storesProposalAndKeepsEdit() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.edit(HALE_LEFT, "Хейл пішов геть.", "Хейл пішов геть.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(HALE_LEFT).userTarget()).isEqualTo("Хейл пішов геть.");
        assertThat(book.openDeferrals())
                .extracting(Deferral::proposal, Deferral::maskedProposal)
                .containsExactly(tuple("Гейл пішов геть.", "Гейл пішов геть."));
        assertThat(report).isEqualTo(new ConsistencyReport(0, 0, 1, List.of("ch1 · p04: proposal recorded")));
    }

    // Running the pass again over a waiting proposal recomputes the same proposal instead of losing it.
    @Test
    void run_secondPassOverProposal_keepsTheSameProposal() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.edit(HALE_LEFT, "Хейл пішов геть.", "Хейл пішов геть.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        ok(book.run(true));

        ok(book.run(true));

        assertThat(book.openDeferrals()).extracting(Deferral::proposal).containsExactly("Гейл пішов геть.");
    }

    private GlossaryEntry term(final String target) {
        final GlossaryEntry went = book.character("went", target, Gender.UNKNOWN, true);
        return new GlossaryEntry(
                went.id(), went.projectId(), went.term(), went.target(), TermType.TERM, went.gender(), went.locked());
    }
}
