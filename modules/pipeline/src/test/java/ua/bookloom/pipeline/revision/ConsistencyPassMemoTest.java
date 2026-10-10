package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
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

    private static final PassOptions EXPORT = new PassOptions(true, false);
    private static final ContextSnapshot SNAPSHOT =
            new ContextSnapshot(List.of("Ранок."), List.of(), List.of(), null, "Keep the narrator's voice.");
    private static final String QUOTED = "«Біжи, Боббі», — сказав я. Він побіг.";
    private static final String UNCHANGED = "{\"unchanged\":true}";

    private void scriptFirstExport(final String draft) {
        book.model()
                .answerTo("reviewer", answer("{\"results\":[]}"))
                .answerTo("draft", RevisionBook.reply(draft))
                .answerTo(CONSISTENCY, answer(UNCHANGED))
                .answerTo(CONSISTENCY, answer(UNCHANGED));
    }

    // IF a flagged segment were drafted again on every export, THEN a second export of the same book paid again.
    @Test
    void runExport_flaggedSegmentRetriedWithoutChange_sendsNoCallOnTheNextExport() {
        ReviewFixtures.update(
                book.desk(),
                RevisionBook.RUN_BOBBY,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget(QUOTED, QUOTED)
                        .withFindings(List.of(new QaFinding("completeness", Severity.HIGH, "a sentence", "dropped"))));
        ReviewFixtures.withContext(book.desk(), RevisionBook.RUN_BOBBY, SNAPSHOT);
        // The retry loses the quote marks, so it is refused and the segment stays as it was.
        scriptFirstExport("Біжи, Боббі, сказав я. Він побіг.");
        ok(book.runExport(EXPORT));
        final int firstExport = book.model().requests().size();

        ok(book.runExport(EXPORT));

        assertThat(firstExport).isPositive();
        assertThat(book.model().requests()).hasSize(firstExport);
    }

    // IF an audit-doubted segment were drafted again on every export, THEN the same doubt cost a call each time.
    @Test
    void runExport_auditDoubtedSegmentRetriedWithoutChange_sendsNoCallOnTheNextExport() {
        final String doubled = "Боббі зайшов зайшов. Він сів.";
        book.decide(RevisionBook.BOBBY, doubled, doubled);
        ReviewFixtures.withContext(book.desk(), RevisionBook.BOBBY, SNAPSHOT);
        // The retry gives the same text back: no better, so the old one is kept.
        scriptFirstExport(doubled);
        ok(book.runExport(EXPORT));
        final int firstExport = book.model().requests().size();

        ok(book.runExport(EXPORT));

        assertThat(firstExport).isPositive();
        assertThat(book.model().requests()).hasSize(firstExport);
    }

    // IF the memory ignored the glossary, THEN a name fixed by the person would never get its paragraph drafted again.
    @Test
    void runExport_glossaryChangedSinceTheLastExport_draftsTheDoubtedSegmentAgain() {
        final String doubled = "Боббі зайшов зайшов. Він сів.";
        book.decide(RevisionBook.BOBBY, doubled, doubled);
        ReviewFixtures.withContext(book.desk(), RevisionBook.BOBBY, SNAPSHOT);
        scriptFirstExport(doubled);
        ok(book.runExport(EXPORT));
        final long firstDrafts = drafts();
        book.add(book.character("Bobby", "Боббі", Gender.MALE, false));
        scriptFirstExport(doubled);

        ok(book.runExport(EXPORT));

        assertThat(drafts()).isEqualTo(firstDrafts + 1);
    }

    private long drafts() {
        return book.model().requests().stream()
                .filter(request -> request.callKind() == CallKind.DRAFT)
                .count();
    }
}
