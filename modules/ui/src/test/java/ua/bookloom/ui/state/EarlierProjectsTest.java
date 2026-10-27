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

/** Which stored projects a dropped file already has: every one whose content hash is the file's, in list order. */
class EarlierProjectsTest {

    private static ProjectSummary summary(final String id, final String name, final String hash, final Path source) {
        return new ProjectSummary(
                new Project(
                        id,
                        source,
                        BookFormat.EPUB,
                        hash,
                        BookBrief.defaults("en"),
                        name,
                        NameOrigin.AUTO,
                        "Earth Gravity",
                        Instant.EPOCH,
                        Instant.EPOCH),
                3,
                9,
                null,
                null);
    }

    // The match is by content, so a copy of the book under another name or folder is still the same book.
    @Test
    void matching_sameContentUnderOtherPaths_returnsEveryProjectInListOrder() {
        final List<ProjectSummary> all = List.of(
                summary("p1", "Earth Gravity · EN → UK", "abc", Path.of("/a/earth.epub")),
                summary("p2", "Other", "zzz", Path.of("/a/other.epub")),
                summary("p3", "Earth Gravity · EN → PL", "abc", Path.of("/b/copy.epub")));

        assertThat(EarlierProjects.matching(all, "abc"))
                .extracting(found -> found.project().id())
                .containsExactly("p1", "p3");
    }

    @Test
    void matching_noProjectHasTheHash_isEmptyEvenWhenAPathWouldMatch() {
        final List<ProjectSummary> all = List.of(summary("p1", "Book", "abc", Path.of("/a/earth.epub")));

        assertThat(EarlierProjects.matching(all, "def")).isEmpty();
    }
}
