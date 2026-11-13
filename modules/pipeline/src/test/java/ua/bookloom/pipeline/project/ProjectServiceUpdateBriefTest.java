package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.pipeline.TestBooks;

/** Saving a brief whose apostrophe changed says how many decided segments it left to look at. */
class ProjectServiceUpdateBriefTest {

    private static final String STRAIGHT = "Мама купила м'яту на ринку.";

    @TempDir
    private Path tempDir;

    private String decidedBook(final ProjectWorld world) {
        final String id = world.create(
                TestBooks.markdown(tempDir.resolve("Book.md"), "Mum bought mint at the market.\n\nShe went home."));
        world.service.updateBrief(
                id, BookBrief.defaults("en").withLanguages("en", "uk").withApostrophe("'"));
        final String first = world.recordsOf(id).getFirst().segmentId();
        world.segments.update(
                id,
                first,
                record -> record.withMachineTarget(STRAIGHT, STRAIGHT).withStatus(SegmentStatus.ACCEPTED));
        return id;
    }

    @Test
    void updateBrief_apostropheChanged_savedCarriesRecheckCount() {
        final ProjectWorld world = new ProjectWorld();
        final String id = decidedBook(world);
        final BookBrief changed = world.stored(id).brief().withApostrophe("’");

        final ProjectChange change = ProjectWorld.changeOf(world.service.updateBrief(id, changed));

        assertThat(change)
                .isInstanceOfSatisfying(
                        ProjectChange.Saved.class,
                        saved -> assertThat(saved.rechecked()).isEqualTo(1));
    }

    @Test
    void updateBrief_nameOnlyChange_noRecheck() {
        final ProjectWorld world = new ProjectWorld();
        final String id = decidedBook(world);
        final BookBrief changed = world.stored(id).brief().withGenre("Fantasy");

        final ProjectChange change = ProjectWorld.changeOf(world.service.updateBrief(id, changed));

        assertThat(change)
                .isInstanceOfSatisfying(
                        ProjectChange.Saved.class,
                        saved -> assertThat(saved.rechecked()).isZero());
        assertThat(world.stored(id).nameOrigin()).isEqualTo(NameOrigin.AUTO);
    }
}
