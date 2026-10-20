package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.Project;

/**
 * The {@link ProjectRepository} finders of the contract — listing, the language pair, the folded name and the book's
 * new location — in their own file by the project's class-length limit.
 */
public abstract class ProjectFindersContractTest extends RepositoryContractTest {

    @Test
    void all_threeSaved_returnsEveryProject() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "One", "en", "uk"));
        repository.save(project("b", "h1", "Two", "en", "de"));
        repository.save(project("c", "h2", "One", "en", "uk"));

        assertThat(repository.all().data()).extracting(Project::id).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void findByPair_sameBookTwoPairs_returnsOnlyTheMatchingPair() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "One", "en", "uk"));
        repository.save(project("b", "h1", "Two", "en", "de"));
        repository.save(project("c", "h2", "One", "en", "uk"));

        assertThat(repository.findByPair("h1", "en", "de").data())
                .hasValueSatisfying(p -> assertThat(p.id()).isEqualTo("b"));
        assertThat(repository.findByPair("h1", "fr", "de").data()).isEmpty();
    }

    @Test
    void findByNameKey_caseDiffers_findsTheProjectOfThatBookOnly() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "Земля", "en", "uk"));
        repository.save(project("c", "h2", "земля", "en", "uk"));

        assertThat(repository.findByNameKey("h1", "земля").data())
                .hasValueSatisfying(p -> assertThat(p.id()).isEqualTo("a"));
        assertThat(repository.findByNameKey("h3", "земля").data()).isEmpty();
    }

    @Test
    void relocateBook_twoProjectsOfTheBook_updatesBothAndLeavesOthers() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "One", "en", "uk"));
        repository.save(project("b", "h1", "Two", "en", "de"));
        repository.save(project("c", "h2", "One", "en", "uk"));

        final Result<List<Project>> moved = repository.relocateBook("h1", Path.of("moved/book.epub"));

        assertThat(moved.data()).extracting(Project::id).containsExactlyInAnyOrder("a", "b");
        assertThat(repository.find("b").data())
                .hasValueSatisfying(p -> assertThat(p.source()).isEqualTo(Path.of("moved/book.epub")));
        assertThat(repository.find("c").data())
                .hasValueSatisfying(p -> assertThat(p.source()).isEqualTo(Path.of("h2.epub")));
    }

    @Test
    void relocateBook_unknownBook_returnsEmptyList() {
        assertThat(projectRepository().relocateBook("none", Path.of("x.epub")).data())
                .isEmpty();
    }

    @Test
    void lastExport_neverExported_isEmpty() {
        assertThat(projectRepository().lastExport("p").data()).isEmpty();
    }

    @Test
    void recordExport_twice_lastExportReturnsTheLaterOneOfThatProjectOnly() {
        final ProjectRepository repository = projectRepository();
        final ExportRecord first = exportRecord("one.epub");
        final ExportRecord second = exportRecord("two.epub");
        repository.recordExport("a", first);
        repository.recordExport("a", second);
        repository.recordExport("b", first);

        assertThat(repository.lastExport("a").data()).hasValue(second);
        assertThat(repository.lastExport("b").data()).hasValue(first);
    }

    private static ExportRecord exportRecord(final String file) {
        return new ExportRecord(
                Instant.parse("2026-10-20T10:00:00Z"), Path.of(file), 74, 2, 0, 1, 50, 6, 74, List.of());
    }
}
