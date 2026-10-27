package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestBooks;

/** Removing a project takes its saved work and nothing else, over the real documents and in-memory repositories. */
class ProjectServiceRemoveTest {

    @TempDir
    private Path tempDir;

    private ProjectWorld world;
    private Path book;
    private String uk;
    private String de;

    @BeforeEach
    void setUp() {
        world = new ProjectWorld();
        book = TestBooks.markdown(
                tempDir.resolve("earth-gravity.md"), "It was a dark night.\n\nThe rain kept falling.\n");
        uk = world.create(book);
        world.translate(uk, "en", "uk");
        de = world.create(book);
        world.translate(de, "en", "de");
        for (final String id : List.of(uk, de)) {
            world.glossary.add(
                    new GlossaryEntry("g-" + id, id, "Mira Holt", "Міра", TermType.CHARACTER, Gender.FEMALE, true));
            world.lexicon.put(LexiconEntry.of(id, "warden"));
            world.runs.save(new RunRecord("r-" + id, id, Instant.EPOCH, Instant.EPOCH, JobState.COMPLETED, 1, 0));
        }
    }

    // IF removing one translation touched its sibling, THEN a person would lose the German work by dropping the
    // Ukrainian.
    @Test
    void remove_oneOfTwoProjects_keepsTheOtherWithItsGlossaryAndRun() {
        final Result<Boolean> removed = world.service.remove(uk);

        assertThat(removed.data()).isTrue();
        assertThat(world.storedProjects()).extracting(project -> project.id()).containsExactly(de);
        assertThat(world.glossary.all(de).data()).hasSize(1);
        assertThat(world.lexicon.all(de).data()).hasSize(1);
        assertThat(world.segments.all(de).data()).hasSize(2);
        assertThat(world.runs.latest(de).data()).isPresent();
    }

    @Test
    void remove_project_dropsItsSegmentsGlossaryLexiconAndRun() {
        world.service.remove(uk);

        assertThat(world.segments.all(uk).data()).isEmpty();
        assertThat(world.glossary.all(uk).data()).isEmpty();
        assertThat(world.lexicon.all(uk).data()).isEmpty();
        assertThat(world.runs.latest(uk).data()).isEmpty();
        assertThat(world.projects.find(uk).data()).isEmpty();
    }

    // IF removal deleted files, THEN the person's book and exported translation would be gone with the project.
    @Test
    void remove_project_keepsTheBookFileAndTheExportedFile() throws IOException {
        final Path exported = Files.writeString(tempDir.resolve("earth-gravity.uk.md"), "Була темна ніч.\n");

        world.service.remove(uk);

        assertThat(book).exists();
        assertThat(exported).hasContent("Була темна ніч.\n");
    }

    @Test
    void remove_lastProjectOfTheBook_leavesNoProject() {
        world.service.remove(uk);
        world.service.remove(de);

        assertThat(world.service.list().data()).isEmpty();
    }

    // IF the removed project kept its book open, THEN the next screens would still plan a book that has no project.
    @Test
    void remove_openProject_closesItsBookSoPlanAnswersValidation() {
        world.service.remove(de);

        final Result<?> plan = world.service.plan(de);

        assertThat(Objects.requireNonNull(plan.error()).code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void remove_unknownProject_answersValidation() {
        final Result<Boolean> removed = world.service.remove("no-such-project");

        assertThat(Objects.requireNonNull(removed.error()).code()).isEqualTo(ErrorCode.validation);
    }
}
