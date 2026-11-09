package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.proofread.SweepBook.DOOR_MASKED;
import static ua.bookloom.pipeline.proofread.SweepBook.DOOR_PLAIN;
import static ua.bookloom.pipeline.proofread.SweepBook.HALE_LEFT;
import static ua.bookloom.pipeline.proofread.SweepBook.SAM_DOOR;
import static ua.bookloom.pipeline.proofread.SweepBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.proofread.SweepBook.ok;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.revision.CodeSweep;

/**
 * The Proofread stage's term sweep over a real opened EPUB, with no model: a locked target is replaced as written, a
 * pinned one in every form, a suggested one never, and the person's edit gets a proposal instead.
 */
class CodeSweepTest {

    @TempDir
    private Path tempDir;

    private SweepBook book;

    @BeforeEach
    void openBook() {
        book = SweepBook.open(tempDir);
    }

    // A locked name the person renamed is replaced whole-word, in both forms; the accepted segment stays accepted.
    @Test
    void sweep_lockedHale_replacedWholeWord() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));

        final CodeSweep.Swept swept = ok(book.sweep());

        final SegmentRecord record = book.stored(HALE_LEFT);
        assertThat(record.machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(record.maskedMachineTarget()).isEqualTo("Гейл пішов.");
        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(swept).isEqualTo(new CodeSweep.Swept(1, 0));
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A pinned name declines, so its genitive is replaced by the new target's genitive.
    @Test
    void sweep_pinnedForm_replaced() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, false));
        book.decide(SAM_MET_HALE, "Сем підійшов до Хейла.", "Сем підійшов до Хейла.");
        book.changeInUkrainian(hale, book.character("Hale", "Гейл", Gender.MALE, false));

        ok(book.sweep());

        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo("Сем підійшов до Гейла.");
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A target that became a mere suggestion was never binding: the text stays and the deferral is resolved.
    @Test
    void sweep_suggestedEntry_untouched() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, false));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, false));
        book.glossaryUpdate(book.character("Hale", null, Gender.MALE, false).withSuggestedTarget("Гейл"));

        assertThat(ok(book.sweep())).isEqualTo(new CodeSweep.Swept(0, 0));

        assertThat(book.stored(HALE_LEFT).machineTarget()).isEqualTo("Хейл пішов.");
        assertThat(book.openDeferrals()).isEmpty();
    }

    // The person's own edit is never rewritten: the swept text waits as a proposal on the term deferral.
    @Test
    void sweep_personEdited_proposalStored() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.edit(HALE_LEFT, "Хейл тихо пішов.", "Хейл тихо пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));

        assertThat(ok(book.sweep())).isEqualTo(new CodeSweep.Swept(0, 1));

        assertThat(book.stored(HALE_LEFT).userTarget()).isEqualTo("Хейл тихо пішов.");
        assertThat(book.openDeferrals())
                .singleElement()
                .extracting(Deferral::proposal)
                .isEqualTo("Гейл тихо пішов.");
    }

    // The sweep knows only a previous glossary target; a rendering the model chose on its own is never swept.
    @Test
    void sweep_renderingModelChose_isNotSwept() {
        final GlossaryEntry hale = book.add(book.character("Hale", null, Gender.MALE, false));
        book.decide(HALE_LEFT, "Хейл прийшов.", "Хейл прийшов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));

        assertThat(ok(book.sweep())).isEqualTo(new CodeSweep.Swept(0, 0));

        assertThat(book.stored(HALE_LEFT).machineTarget()).isEqualTo("Хейл прийшов.");
        assertThat(book.stored(HALE_LEFT).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A term the person unlocked again after the change stays pinned, which still binds: it is swept all the same.
    @Test
    void sweep_termUnlockedAfterChange_sweptAsPinned() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        book.glossaryUpdate(book.character("Hale", "Гейл", Gender.MALE, false));

        ok(book.sweep());

        assertThat(book.stored(HALE_LEFT).machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(book.stored(HALE_LEFT).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(book.openDeferrals()).isEmpty();
    }

    // A target that no longer holds the old rendering has nothing to sweep; its deferral is resolved silently.
    @Test
    void sweep_targetNoLongerHoldsOldRendering_resolvesAndKeepsTarget() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Він пішов.", "Він пішов.");

        ok(book.sweep());

        assertThat(book.stored(HALE_LEFT).machineTarget()).isEqualTo("Він пішов.");
        assertThat(book.stored(HALE_LEFT).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(book.openDeferrals()).isEmpty();
    }

    // Two changed terms in one segment are both swept into the one stored target.
    @Test
    void sweep_twoTermsInOneSegment_sweepsBoth() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        final GlossaryEntry went = book.add(term("пішов"));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        book.change(went, term("вийшов"));

        final CodeSweep.Swept swept = ok(book.sweep());

        assertThat(book.stored(HALE_LEFT).maskedMachineTarget()).isEqualTo("Гейл вийшов.");
        assertThat(swept).isEqualTo(new CodeSweep.Swept(2, 0));
    }

    // A swept term keeps the segment's placeholders: the plain form is restored from the swept masked form.
    @Test
    void sweep_termInFormattedSegment_restoresPlainFromMasked() {
        final GlossaryEntry sam = book.add(book.character("Sam", "Сем", Gender.MALE, true));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.change(sam, book.character("Sam", "Семюел", Gender.MALE, true));

        ok(book.sweep());

        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo("Семюел відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        assertThat(book.stored(SAM_DOOR).machineTarget()).isEqualTo("Семюел відчинив <em>старі</em> двері.");
    }

    // Sweeping again over a waiting proposal recomputes the same proposal instead of losing it.
    @Test
    void sweep_secondSweepOverProposal_keepsTheSameProposal() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.edit(HALE_LEFT, "Хейл пішов геть.", "Хейл пішов геть.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));
        ok(book.sweep());

        ok(book.sweep());

        assertThat(book.openDeferrals()).extracting(Deferral::proposal).containsExactly("Гейл пішов геть.");
    }

    // A term renamed after a proofread proposal is swept into that proposal: one proposal waits and holds both fixes.
    @Test
    void sweep_termRenamedAfterProposal_oneProposalHoldsBothFixes() {
        final GlossaryEntry sam = book.add(book.character("Sam", "Сем", Gender.FEMALE, true));
        book.edit(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordDeferral(new Deferral(
                "proposal-1",
                book.desk().projectId(),
                SAM_DOOR,
                DeferralReason.PROOFREAD,
                null,
                null,
                "Сем відчинила <em>старі</em> двері.",
                "Сем відчинила ⟦g0⟧старі⟦g1⟧ двері."));
        book.change(sam, book.character("Sam", "Саманта", Gender.FEMALE, true));

        ok(book.sweep());

        assertThat(book.openDeferrals())
                .filteredOn(deferral -> deferral.proposal() != null)
                .extracting(Deferral::proposal, Deferral::maskedProposal)
                .containsExactly(
                        tuple("Саманта відчинила <em>старі</em> двері.", "Саманта відчинила ⟦g0⟧старі⟦g1⟧ двері."));
        final SegmentRecord applied = ok(book.desk()
                .reviewDesk(ReviewMode.UNATTENDED)
                .acceptProposal(book.desk().projectId(), SAM_DOOR));
        assertThat(applied.maskedUserTarget()).isEqualTo("Саманта відчинила ⟦g0⟧старі⟦g1⟧ двері.");
        assertThat(book.openDeferrals())
                .filteredOn(deferral -> deferral.segmentId().equals(SAM_DOOR))
                .isEmpty();
    }

    private GlossaryEntry term(final String target) {
        final GlossaryEntry went = book.character("went", target, Gender.UNKNOWN, true);
        return GlossaryEntry.of(
                went.id(),
                went.projectId(),
                went.term(),
                went.target(),
                ua.bookloom.api.project.TermType.TERM,
                went.gender(),
                went.locked());
    }
}
