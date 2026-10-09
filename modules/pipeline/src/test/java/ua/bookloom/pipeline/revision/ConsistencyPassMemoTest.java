package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.review.ReviewFixtures;

/** What the consistency pass remembers of a checked paragraph, and when it forgets (task 15h.B2). */
class ConsistencyPassMemoTest {

    private static final String CONSISTENCY = "consistency";
    private static final String BEFORE = "Сем зустрів Гейла.";
    private static final String FIXED = "Сем зустрів Гейла вранці.";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        book.add(book.character("Hale", "Гейл", Gender.MALE, false));
        book.decide("ch01.xhtml:6", "Ранок.", "Ранок.");
        book.decide("ch01.xhtml:8", "Вечір.", "Вечір.");
        ReviewFixtures.update(
                book.desk(),
                SAM_MET_HALE,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(BEFORE, BEFORE)
                        .withPath(SegmentPath.REPAIRED));
    }

    private static Result<ChatResponse> answer(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    // IF the memory ignored the neighbours, THEN a changed neighbour would never get its paragraph checked again.
    @Test
    void run_neighbourChangedSinceTheLastPass_checksTheParagraphAgain() {
        book.model()
                .answerTo(CONSISTENCY, answer("{\"unchanged\":true}"))
                .answerTo(CONSISTENCY, answer("{\"unchanged\":true}"));
        ok(book.run(true));
        book.decide("ch01.xhtml:8", "Ніч.", "Ніч.");

        ok(book.run(true));

        assertThat(book.model().requests()).hasSize(2);
    }

    // IF a stored fix were remembered as checked, THEN the fixed text would never be looked at once more.
    @Test
    void run_fixStored_checksTheNewTextOnTheNextPass() {
        book.model()
                .answerTo(CONSISTENCY, answer("{\"target\":\"" + FIXED + "\"}"))
                .answerTo(CONSISTENCY, answer("{\"unchanged\":true}"));
        ok(book.run(true));
        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(FIXED);

        ok(book.run(true));

        assertThat(book.model().requests()).hasSize(2);
    }

    // IF an unreadable reply were remembered, THEN a paragraph the model merely garbled once would be skipped for good.
    @Test
    void run_unreadableReply_isAskedAgainOnTheNextPass() {
        book.model().answerTo(CONSISTENCY, answer("not json")).answerTo(CONSISTENCY, answer("{\"unchanged\":true}"));
        ok(book.run(true));

        ok(book.run(true));

        assertThat(book.model().requests()).hasSize(2);
    }

    // The old way of saying nothing changed, writing the paragraph again, is still read.
    @Test
    void run_paragraphWrittenAgainAsItWas_isUnchangedAndRemembered() {
        book.model().answerTo(CONSISTENCY, answer("{\"target\":\"" + BEFORE + "\"}"));

        final ConsistencyReport first = ok(book.run(true));
        ok(book.run(true));

        assertThat(first.checks().neighbourUnchanged()).isEqualTo(1);
        assertThat(book.model().requests()).hasSize(1);
    }
}
