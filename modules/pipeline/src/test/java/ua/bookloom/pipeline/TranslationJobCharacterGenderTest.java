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
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A glossary woman (Chisel, an invented consonant name) whose draft puts a masculine verb after her name: the soft
 * finding earns exactly one directed fix, seen through a whole run on the scripted model.
 */
class TranslationJobCharacterGenderTest {

    private static final String SOURCE = "Chisel opened the old door.";
    private static final String WRONG = "Чизел відчинив старі двері.";
    private static final String RIGHT = "Чизел відчинила старі двері.";

    @TempDir
    private Path tempDir;

    private TestProject projectWith(final Gender gender) {
        final Path book = TestBooks.txt(tempDir.resolve("Book.txt"), SOURCE);
        final TestProject project = project(book, brief("en", "uk"));
        final GlossaryEntry chisel = new GlossaryEntry(
                GlossaryIds.of(project.id(), "Chisel"),
                project.id(),
                "Chisel",
                "Чизел",
                TermType.CHARACTER,
                gender,
                false);
        Objects.requireNonNull(project.stores().glossary().add(chisel).data(), "added");
        return project;
    }

    @Test
    void run_masculineVerbAfterAWomansName_isRepairedWithOneDirectedFix() {
        final ScriptedChatModel model = replies(WRONG, RIGHT);
        final TestProject project = projectWith(Gender.FEMALE);

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(userMessage(model.requests().get(1))).contains("відчинив").contains("gender");
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, RIGHT);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("name-gender");
    }

    @Test
    void run_fixStillMasculine_keepsTheDraftWithTheNoteAndSpendsNoSecondFix() {
        final ScriptedChatModel model = replies(WRONG, WRONG);
        final TestProject project = projectWith(Gender.FEMALE);

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.DRAFT, WRONG);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .contains("name-gender");
    }

    @Test
    void run_theCharacterIsAMan_makesNoFixCall() {
        final ScriptedChatModel model = replies(WRONG);
        final TestProject project = projectWith(Gender.MALE);

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT);
    }
}
