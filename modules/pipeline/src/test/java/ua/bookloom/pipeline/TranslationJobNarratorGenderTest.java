package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A first-person male narrator whose draft says «відчинила»: the soft gender finding earns exactly one directed fix,
 * seen through a whole run on the scripted model.
 */
class TranslationJobNarratorGenderTest {

    private static final String WRONG = "Я відчинила старі двері.";
    private static final String RIGHT = "Я відчинив старі двері.";

    @TempDir
    private Path tempDir;

    private static BookBrief maleNarrator(final QualityDial dial) {
        return brief("en", "uk", dial).withNarrator(new Narrator(NarratorPerson.FIRST, Gender.MALE));
    }

    @Test
    void run_wrongGenderThenFix_repairsTheSegmentWithOneDirectedFix() {
        final ScriptedChatModel model = replies(WRONG, RIGHT);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), maleNarrator(QualityDial.FAST));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(userMessage(model.requests().get(1))).contains("відчинила").contains("gender");
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, RIGHT);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("gender");
    }

    @Test
    void run_wrongGenderFixStillWrong_keepsTheDraftAcceptedWithTheNoteAndSpendsNoSecondFix() {
        final ScriptedChatModel model = replies(WRONG, WRONG);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), maleNarrator(QualityDial.FAST));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.DRAFT, WRONG);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .contains("gender");
    }

    @Test
    void run_wrongGenderReviewedByTheReviewer_isFixedAfterTheReviewerAnswered() {
        final ScriptedChatModel model = replies(WRONG).answer(reviewed());
        model.answer(ChunkRunFixtures.target(RIGHT));
        final TestProject project = project(ChunkRunFixtures.door(tempDir), maleNarrator(QualityDial.BALANCED));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, REVIEW, FIX);
        assertThat(stored(project, "Book.txt:0").machineTarget()).isEqualTo(RIGHT);
    }

    @Test
    void run_thirdPersonNarrator_makesNoFixCall() {
        final BookBrief third = brief("en", "uk").withNarrator(new Narrator(NarratorPerson.THIRD, Gender.MALE));
        final ScriptedChatModel model = replies(WRONG);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), third);

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT);
    }

    @Test
    void run_noNarratorStated_makesNoFixCall() {
        final ScriptedChatModel model = replies(WRONG);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk"));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT);
    }
}
