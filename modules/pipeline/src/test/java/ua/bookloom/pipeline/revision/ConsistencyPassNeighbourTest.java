package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * The consistency pass's check of a repaired paragraph against the paragraphs around it: what the call is shown, what
 * is replaced, and what is never touched.
 */
class ConsistencyPassNeighbourTest {

    private static final String PREVIOUS = "ch01.xhtml:6";
    private static final String NEXT = "ch01.xhtml:8";
    private static final String CONSISTENCY = "consistency";
    private static final String BEFORE = "Сем зустрів Хейла.";
    private static final String AFTER = "Сем зустрів Гейла.";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        book.add(book.character("Hale", "Гейл", Gender.MALE, false));
        book.decide(PREVIOUS, "Ранок.", "Ранок.");
        book.decide(NEXT, "Вечір.", "Вечір.");
    }

    private void repaired(final String segmentId, final String text) {
        ReviewFixtures.update(
                book.desk(),
                segmentId,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(text, text)
                        .withPath(SegmentPath.REPAIRED));
    }

    // IF the check did not show the neighbours and the names, THEN it could not tell a drifted name from a right one.
    @Test
    void run_repairedParagraph_isCheckedWithItsNeighboursAndNamesAndReplaced() {
        repaired(SAM_MET_HALE, BEFORE);
        book.model().answerTo(CONSISTENCY, reply(AFTER));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.userMessage(0))
                .contains("[Previous paragraph, translated]\nРанок.", "[Next paragraph, translated]\nВечір.")
                .contains("- Hale → Гейл, male")
                .contains("<Translation>\n" + BEFORE + "\n</Translation>");
        assertThat(book.stored(SAM_MET_HALE))
                .extracting(record -> record.machineTarget(), record -> record.status())
                .containsExactly(AFTER, SegmentStatus.REVISED);
        assertThat(report.neighbourFixes()).isEqualTo(1);
        assertThat(report.notes()).containsExactly("ch1 · p08: fixed against its neighbours");
    }

    // IF an unchanged answer were stored, THEN every checked paragraph would be marked revised for nothing.
    @Test
    void run_answerThatChangesNothing_storesNothing() {
        repaired(SAM_MET_HALE, BEFORE);
        book.model().answerTo(CONSISTENCY, reply(BEFORE));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(report.neighbourFixes()).isZero();
        assertThat(book.stored(SAM_MET_HALE))
                .extracting(record -> record.machineTarget(), record -> record.status())
                .containsExactly(BEFORE, SegmentStatus.ACCEPTED);
    }

    // IF a reply that writes « » as U+001C-U+001F were trimmed or kept, THEN the dialogue loses its quote marks.
    @Test
    void run_answerWithControlCharactersForQuotes_keepsTheOldText() {
        repaired(SAM_MET_HALE, BEFORE);
        book.model().answerTo(CONSISTENCY, reply("\\u001eСем зустрів Гейла\\u001d, \\u0013сказав я."));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(report.neighbourFixes()).isZero();
        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(BEFORE);
    }

    // IF clean paragraphs were checked too, THEN the pass would cost one model call per paragraph of the book.
    @Test
    void run_cleanAcceptedDraft_isNotSentToTheModel() {
        book.decide(SAM_MET_HALE, BEFORE, BEFORE);

        ok(book.run(true));

        assertThat(book.model().requests()).isEmpty();
    }

    // IF the person's own text were sent, THEN a check could overwrite what they wrote.
    @Test
    void run_personEditedParagraph_isNeverChecked() {
        book.edit(SAM_MET_HALE, BEFORE, BEFORE);
        ReviewFixtures.update(book.desk(), SAM_MET_HALE, record -> record.withPath(SegmentPath.REPAIRED));

        ok(book.run(true));

        assertThat(book.model().requests()).isEmpty();
    }

    @Test
    void run_noModel_checksNothing() {
        repaired(SAM_MET_HALE, BEFORE);

        final ConsistencyReport report = ok(book.run(false));

        assertThat(report.neighbourFixes()).isZero();
        assertThat(book.model().requests()).isEmpty();
    }

    // IF one paragraph's failed call ended the pass, THEN an export would write no book because of a timeout.
    @Test
    void run_oneCallFails_theRestOfThePassAndTheReportSurvive() {
        repaired(SAM_MET_HALE, BEFORE);
        book.model()
                .answerTo(CONSISTENCY, Result.err(AppError.of(ErrorCode.timeout, "Slow", "The model took too long.")));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(report.neighbourFixes()).isZero();
        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(BEFORE);
    }

    // IF a paragraph that only carried a soft note were checked, THEN every export would pay a call for it again.
    @Test
    void run_acceptedDraftWithOnlyASoftNote_isNotChecked() {
        book.decide(SAM_MET_HALE, BEFORE, BEFORE);
        ReviewFixtures.update(
                book.desk(),
                SAM_MET_HALE,
                record -> record.withFindings(List.of(new QaFinding("fluency", Severity.LOW, "a note", "spacing"))));

        ok(book.run(true));

        assertThat(book.model().requests()).isEmpty();
    }
}
