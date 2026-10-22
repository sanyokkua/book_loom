package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;

/**
 * A file whose content is already stored as projects is named, never imported again: importing would reset the stored
 * project's segments. The match is by content hash of the file's bytes, read off the FX thread; no project is removed.
 */
class ImportGuardAlreadyTest extends ImportGuardTestBase {

    private static final String UK = "Earth Gravity · EN → UK";
    private static final String PL = "Earth Gravity · EN → PL";

    private void listTwoProjectsOfAbc() {
        projects.willList(
                List.of(summary("p1", UK, ABC_HASH), summary("p2", PL, ABC_HASH), summary("p3", "Other", "zz")));
    }

    // IF the book were imported again, THEN the stored projects' segments would be reset behind the person's back.
    @Test
    void requestImport_contentMatchesTwoProjects_showsBothAndImportsNothing() throws IOException {
        final Path file = fileWithKnownContent("earth.epub");
        listTwoProjectsOfAbc();

        requestImport(file);

        assertThat(shown())
                .isEqualTo(new ImportState.AlreadyInProjects("earth.epub", file, List.of(UK, PL), "Earth Gravity"));
        assertThat(projects.imports()).isEmpty();
        assertThat(projects.removedProjects()).isEmpty();
        assertThat(prompt.asked()).isEmpty();
    }

    // The match is by content, so the same bytes under another name are the same book.
    @Test
    void requestImport_sameBytesUnderAnotherFileName_stillMatches() throws IOException {
        final Path copy = fileWithKnownContent("renamed copy.epub");
        listTwoProjectsOfAbc();

        requestImport(copy);

        assertThat(shown()).isInstanceOf(ImportState.AlreadyInProjects.class);
        assertThat(projects.imports()).isEmpty();
    }

    @Test
    void requestImport_contentHasNoProject_importsTheBook() throws IOException {
        final Path file = fileWithKnownContent("fresh.epub");
        projects.willList(List.of(summary("p3", "Other", "zz")));
        projects.on(file, Result.ok(BookFixtures.frankensteinImport()));

        requestImport(file);

        assertThat(projects.imports()).containsExactly(file);
        assertThat(shown()).isInstanceOf(ImportState.Detected.class);
    }

    // A failed hash or listing never stops a book from opening: it counts as no duplicate.
    @Test
    void requestImport_fileCannotBeRead_opensWithoutADuplicateCheck() {
        listTwoProjectsOfAbc();

        requestImport(DRACULA);

        assertThat(projects.imports()).containsExactly(DRACULA);
    }

    // The question comes first; only after "Open as a new project" is the file read and found to be a known book.
    @Test
    void openNew_openProjectAndTheSameBookDropped_asksFirstThenShowsAlreadyInProjects() throws IOException {
        openFirstBook();
        final Path file = fileWithKnownContent("earth.epub");
        listTwoProjectsOfAbc();

        requestImport(file);

        assertThat(prompt.asked()).hasSize(1);
        assertThat(shown()).isInstanceOf(ImportState.Detected.class);

        openNew();

        assertThat(shown()).isInstanceOf(ImportState.AlreadyInProjects.class);
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
    }

    // IF replace skipped the lookup, THEN creating over another stored copy of the book would reset its segments.
    @Test
    void replace_fileIsStoredAsAnotherProject_showsAlreadyAndRemovesNothing() throws IOException {
        openFirstBook();
        final Path file = fileWithKnownContent("earth.epub");
        projects.willList(List.of(summary("p1", OPEN_NAME, "other-hash"), summary("p2", PL, ABC_HASH)));

        requestImport(file);
        replace();

        assertThat(shown())
                .isEqualTo(new ImportState.AlreadyInProjects("earth.epub", file, List.of(PL), "Earth Gravity"));
        assertThat(projects.removedProjects()).isEmpty();
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
    }

    // The project being replaced is no duplicate of itself: replacing it with the same book still goes ahead.
    @Test
    void replace_fileIsOnlyTheOpenProjectsOwnBook_removesItThenImports() throws IOException {
        openFirstBook();
        final Path file = fileWithKnownContent("earth.epub");
        projects.on(file, Result.ok(BookFixtures.frankensteinImport()));
        projects.willList(List.of(summary("p1", OPEN_NAME, ABC_HASH)));

        requestImport(file);
        replace();

        assertThat(projects.removedProjects()).containsExactly("p1");
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, file);
    }
}
