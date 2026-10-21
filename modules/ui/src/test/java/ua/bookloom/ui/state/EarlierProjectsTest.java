package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** Which stored project a file would reuse: the one whose source is that file's absolute, normalized path. */
class EarlierProjectsTest {

    private static ProjectSummary summary(final String id, final Path source, final int decided) {
        return new ProjectSummary(
                new Project(
                        id,
                        source,
                        BookFormat.EPUB,
                        "hash",
                        BookBrief.defaults("en"),
                        "Book",
                        NameOrigin.AUTO,
                        Instant.EPOCH,
                        Instant.EPOCH),
                decided);
    }

    @Test
    void find_projectWithTheSameSource_returnsIt() {
        final Path source = Path.of("/books/Dracula.epub");
        final List<ProjectSummary> all =
                List.of(summary("p1", Path.of("/books/Other.epub"), 3), summary("p2", source, 7));

        assertThat(EarlierProjects.find(all, source)).hasValueSatisfying(found -> {
            assertThat(found.project().id()).isEqualTo("p2");
            assertThat(found.decidedSegments()).isEqualTo(7);
        });
    }

    @Test
    void find_sourceGivenWithDotSegments_matchesTheNormalizedProject() {
        final List<ProjectSummary> all = List.of(summary("p1", Path.of("/books/Dracula.epub"), 2));

        assertThat(EarlierProjects.find(all, Path.of("/books/../books/Dracula.epub")))
                .isPresent();
    }

    @Test
    void find_noProjectForTheSource_isEmpty() {
        final List<ProjectSummary> all = List.of(summary("p1", Path.of("/books/Other.epub"), 2));

        assertThat(EarlierProjects.find(all, Path.of("/books/Dracula.epub"))).isEmpty();
    }
}
