package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
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
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A Ukrainian draft with a Russian-only letter (the run's бэкона, кобыли): the blocking alphabet finding earns the
 * directed fix that names the word, and a draft that still holds the letter after the repair round is flagged.
 */
class TranslationJobAlphabetTest {

    private static final String RUSSIAN_LETTER = "Я відчинив стары двері.";
    private static final String RIGHT = "Я відчинив старі двері.";

    @TempDir
    private Path tempDir;

    @Test
    void run_russianLetterThenFix_repairsTheSegmentWithOneDirectedFixNamingTheWord() {
        final ScriptedChatModel model = replies(RUSSIAN_LETTER, RIGHT);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.FAST));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(userMessage(model.requests().get(1))).contains("стары").contains("ы");
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, RIGHT);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("alphabet");
    }

    @Test
    void run_fixKeepsTheRussianLetter_flagsTheSegmentWithTheAlphabetFindingAndKeepsTheDraftAsMachineTarget() {
        final ScriptedChatModel model = replies(RUSSIAN_LETTER, RUSSIAN_LETTER);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.FAST));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.FLAGGED, RUSSIAN_LETTER);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .contains("alphabet");
    }
}
