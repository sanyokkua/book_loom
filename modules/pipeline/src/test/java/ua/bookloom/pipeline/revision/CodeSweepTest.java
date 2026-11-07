package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.HALE_LEFT;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The Proofread stage's term sweep over a real opened EPUB, with no model: a locked target is replaced as written, a
 * pinned one in every form, a suggested one never, and the person's edit gets a proposal instead.
 */
class CodeSweepTest {

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
    }

    // A locked name the person renamed is replaced whole-word; the accepted segment stays accepted.
    @Test
    void sweep_lockedHale_replacedWholeWord() {
        final GlossaryEntry hale = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.decide(HALE_LEFT, "Хейл пішов.", "Хейл пішов.");
        book.change(hale, book.character("Hale", "Гейл", Gender.MALE, true));

        final CodeSweep.Swept swept = ok(book.sweep());

        final SegmentRecord record = book.stored(HALE_LEFT);
        assertThat(record.machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(swept).isEqualTo(new CodeSweep.Swept(1, 0));
        assertThat(book.model().requests()).isEmpty();
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
}
