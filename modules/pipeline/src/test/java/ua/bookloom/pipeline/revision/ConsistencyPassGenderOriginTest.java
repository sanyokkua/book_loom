package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_MASKED;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_PLAIN;
import static ua.bookloom.pipeline.revision.RevisionBook.DOOR_REVISED;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_DOOR;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * Only a gender the book verified or the person set is acted on by the pass (15i.T1): the backward revision of the
 * segments that waited on a character, and the facts and sheet the neighbour check reads. A gender only suggested is
 * neither re-rendered into the book nor offered to the model as a fact.
 */
class ConsistencyPassGenderOriginTest {

    private static final String REVISION = "revision";
    private static final String CONSISTENCY = "consistency";
    private static final String BEFORE = "Сем зустрів Гейла.";
    private static final PassOptions EXPORT = new PassOptions(true, false);

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
    }

    private GlossaryEntry sam(final Gender gender) {
        return book.character("Sam", "Сем", Gender.UNKNOWN, false).withInferredGender(gender);
    }

    private void samDoorWaitsOnGender() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false));
        book.decide(SAM_DOOR, DOOR_PLAIN, DOOR_MASKED);
        book.recordUnknownGender(SAM_DOOR);
    }

    private void repaired() {
        book.add(book.character("Hale", "Гейл", Gender.MALE, false));
        ReviewFixtures.update(
                book.desk(),
                SAM_MET_HALE,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(BEFORE, BEFORE)
                        .withPath(SegmentPath.REPAIRED));
        book.model().answerTo(CONSISTENCY, reply(BEFORE));
    }

    // IF a suggested gender were revised into the book, THEN one wrong guess would rewrite every paragraph that waits
    // on the character, with a model call each.
    @Test
    void run_genderOnlySuggested_makesNoRevisionCallAndKeepsTheDeferral() {
        samDoorWaitsOnGender();
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.UNKNOWN, false).withSuggestedGender(Gender.FEMALE));
        book.model().answerTo(REVISION, reply(DOOR_REVISED));

        ok(book.run(true));

        assertThat(book.model().requests()).isEmpty();
        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_MASKED);
        assertThat(book.openDeferrals()).hasSize(1);
    }

    // The deferral waits, so the gender confirmed later still rewrites the paragraph.
    @Test
    void run_genderSuggestedThenVerified_revisesOnTheRunAfterTheVerdict() {
        samDoorWaitsOnGender();
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.UNKNOWN, false).withSuggestedGender(Gender.FEMALE));
        book.model().answerTo(REVISION, reply(DOOR_REVISED));
        ok(book.run(true));
        final int callsAfterTheGuess = book.model().requests().size();
        book.glossaryUpdate(sam(Gender.FEMALE));
        book.model().answerTo(REVISION, reply(DOOR_REVISED));

        ok(book.run(true));

        assertThat(callsAfterTheGuess).isZero();
        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_REVISED);
        assertThat(book.openDeferrals()).isEmpty();
    }

    @Test
    void run_genderVerified_reRendersThroughOneCall() {
        samDoorWaitsOnGender();
        book.glossaryUpdate(sam(Gender.FEMALE));
        book.model().answerTo(REVISION, reply(DOOR_REVISED));

        ok(book.run(true));

        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.userMessage(0)).contains("- Sam (Сем): female");
        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_REVISED);
    }

    @Test
    void run_genderSetByThePerson_reRendersThroughOneCall() {
        samDoorWaitsOnGender();
        book.glossaryUpdate(book.character("Sam", "Сем", Gender.UNKNOWN, false).withGender(Gender.FEMALE));
        book.model().answerTo(REVISION, reply(DOOR_REVISED));

        ok(book.run(true));

        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.stored(SAM_DOOR).maskedMachineTarget()).isEqualTo(DOOR_REVISED);
    }

    // IF the neighbour check were told a suggested gender as a fact, THEN it would rewrite a right paragraph to match
    // a guess; the facts line names the rendering only and the sheet leaves the character out.
    @Test
    void run_genderOnlySuggested_neighbourPromptCarriesNoGenderForTheCharacter() {
        book.add(book.character("Sam", "Сем", Gender.UNKNOWN, false).withSuggestedGender(Gender.FEMALE));
        repaired();

        ok(book.runExport(EXPORT));

        assertThat(book.userMessage(0))
                .contains("- Sam → Сем")
                .contains("- Hale → Гейл, male")
                .contains("Hale — male")
                .doesNotContain("Сем, female")
                .doesNotContain("Sam —")
                .doesNotContain("female")
                .doesNotContain("likely");
    }

    @Test
    void run_genderVerified_neighbourPromptCarriesTheFactAndTheSheetLine() {
        book.add(sam(Gender.FEMALE));
        repaired();

        ok(book.runExport(EXPORT));

        assertThat(book.userMessage(0)).contains("- Sam → Сем, female").contains("Sam — female");
    }
}
