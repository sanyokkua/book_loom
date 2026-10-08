package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.BOBBY;
import static ua.bookloom.pipeline.revision.RevisionBook.RUN_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    // IF a flagged paragraph's fix were held to the same counts as a word fix, THEN a fix restoring the sentence the
    // draft dropped would always be refused and the call wasted.
    @Test
    void run_flaggedParagraphWhoseFixRestoresADroppedSentence_isStored() {
        final String dropped = "Боббі зайшов.";
        final String whole = "Боббі зайшов. Він сів.";
        ReviewFixtures.update(
                book.desk(),
                BOBBY,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget(dropped, dropped)
                        .withFindings(List.of(new QaFinding("completeness", Severity.HIGH, "a sentence", "dropped"))));
        book.model().answerTo(CONSISTENCY, reply(whole));

        final ConsistencyReport report = ok(book.run(true));

        // A neighbour fix repairs unnamed defects, so the recorded finding keeps the segment FLAGGED for the person.
        assertThat(book.stored(BOBBY))
                .extracting(record -> record.machineTarget(), record -> record.status())
                .containsExactly(whole, SegmentStatus.FLAGGED);
        assertThat(report.neighbourFixes()).isEqualTo(1);
        assertThat(report.checks().refused()).isEmpty();
    }

    // IF the check did not show the neighbours and the names, THEN it could not tell a drifted name from a right one.
    @Test
    void run_repairedParagraph_isCheckedWithItsNeighboursAndNamesAndReplaced() {
        repaired(SAM_MET_HALE, BEFORE);
        book.model().answerTo(CONSISTENCY, reply(AFTER));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.userMessage(0))
                .contains(
                        "Source: Chapter 1 line 6.\nTranslation: Ранок.",
                        "Source: Chapter 1 line 8.\nTranslation: Вечір.")
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

    // IF an answer that is the old text worse could replace it, THEN a model's slip lands in the book: each row is a
    // regression a real consistency pass made.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "«Сем зустрів Гейла».|Сем зустрів Гейла.",
                "Сем зустрів Гейла для протидії забуттю.|Сем зустрів Гейла для senile amnesia.",
                "Сем зустрів Гейла, що купив у свого знайомого двері.|Сем зустрів Гейла, що купив від.",
                "Сем зустрів Гейла. Він пішов.|Сем зустрів Гейла, він пішов.",
                "— Сем зустрів Гейла.|Сем зустрів Гейла."
            })
    void run_answerThatLosesQuotesDashesSentencesWordsOrAddsForeignWords_keepsTheOldText(
            final String old, final String answer) {
        repaired(SAM_MET_HALE, old);
        book.model().answerTo(CONSISTENCY, reply(answer));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(report.neighbourFixes()).isZero();
        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(old);
    }

    // IF the glossary were not passed to the checks, THEN an answer that drops the name the source calls out would
    // stay.
    @Test
    void run_answerThatDropsTheNameTheSourceCallsOut_keepsTheOldText() {
        repaired(RUN_HALE, "Біжи, Гейле.");
        book.model().answerTo(CONSISTENCY, reply("Біжи."));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(report.neighbourFixes()).isZero();
        assertThat(book.stored(RUN_HALE).machineTarget()).isEqualTo("Біжи, Гейле.");
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
