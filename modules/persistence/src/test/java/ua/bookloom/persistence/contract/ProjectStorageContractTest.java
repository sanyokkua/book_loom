package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;

/**
 * The storage rules a project and its book keep across adapters ({@code specs/local-storage/spec.md}): a name is
 * unique within one book ignoring case, one book is shared by its content, removing a project removes its work and
 * nothing else, and a person's glossary edit and a run's glossary addition never overwrite each other. Continues
 * {@link LexiconContractTest} in its own file by the project's class-length limit.
 */
public abstract class ProjectStorageContractTest extends LexiconContractTest {

    @Test
    void save_cyrillicNameDifferingByCaseInOneBook_answersValidationAndStoresNothing() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "Земля", "en", "uk"));

        final Result<Project> clash = repository.save(project("b", "h1", "земля", "en", "de"));

        assertThat(clash.isErr()).isTrue();
        assertThat(clash.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(repository.find("b").data()).isEmpty();
        assertThat(repository.find("a").data())
                .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("Земля"));
    }

    @Test
    void save_renameToAnotherProjectsNameInOtherCase_answersValidationAndKeepsTheOldName() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "Земля", "en", "uk"));
        repository.save(project("b", "h1", "Other", "en", "de"));

        final Result<Project> rename = repository.save(project("b", "h1", " ЗЕМЛЯ ", "en", "de"));

        assertThat(rename.isErr()).isTrue();
        assertThat(rename.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(repository.find("b").data())
                .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("Other"));
    }

    @Test
    void save_sameNameInAnotherBook_isAccepted() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "Земля", "en", "uk"));

        assertThat(repository.save(project("c", "h2", "Земля", "en", "uk")).isOk())
                .isTrue();
        assertThat(repository.all().data()).extracting(Project::id).containsExactlyInAnyOrder("a", "c");
    }

    @Test
    void save_projectRenamedToItsOwnNameInOtherCase_isAccepted() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "Земля", "en", "uk"));

        assertThat(repository.save(project("a", "h1", "ЗЕМЛЯ", "en", "uk")).isOk())
                .isTrue();
        assertThat(repository.find("a").data())
                .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("ЗЕМЛЯ"));
    }

    @Test
    void save_sameContentUnderAnotherFileName_sharesTheOneStoredBookAtItsFirstPath() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "One", "en", "uk"));

        repository.save(withSource(project("b", "h1", "Two", "en", "de"), Path.of("copy.epub")));

        assertThat(repository.find("a").data())
                .hasValueSatisfying(p -> assertThat(p.source()).isEqualTo(Path.of("h1.epub")));
        assertThat(repository.find("b").data())
                .hasValueSatisfying(p -> assertThat(p.source()).isEqualTo(Path.of("h1.epub")));
    }

    @Test
    void save_staleProjectAfterRelocateBook_keepsTheRelocatedPath() {
        final ProjectRepository repository = projectRepository();
        final Project stale = project("a", "h1", "One", "en", "uk");
        repository.save(stale);
        repository.relocateBook("h1", Path.of("moved.epub"));

        repository.save(stale);

        assertThat(repository.find("a").data())
                .hasValueSatisfying(p -> assertThat(p.source()).isEqualTo(Path.of("moved.epub")));
    }

    @Test
    void delete_oneOfTwoProjectsOfABook_removesItsWorkAndKeepsTheOther() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("p1", "h1", "One", "en", "uk"));
        repository.save(project("p2", "h1", "Two", "en", "de"));
        storeWork("p1");
        storeWork("p2");

        assertThat(repository.delete("p1").data()).isTrue();

        assertThat(segmentRepository().all("p1").data()).isEmpty();
        assertThat(glossaryRepository().all("p1").data()).isEmpty();
        assertThat(tmRepository().exact("p1", "h1").data()).isEmpty();
        assertThat(summaryRepository().latest("p1").data()).isEmpty();
        assertThat(lexiconRepository().all("p1").data()).isEmpty();
        assertThat(segmentRepository().all("p2").data()).hasSize(1);
        assertThat(glossaryRepository().all("p2").data()).hasSize(1);
        assertThat(tmRepository().exact("p2", "h1").data()).hasSize(1);
        assertThat(summaryRepository().latest("p2").data()).isPresent();
        assertThat(lexiconRepository().all("p2").data()).hasSize(1);
        assertThat(repository.find("p2").data())
                .hasValueSatisfying(p -> assertThat(p.source()).isEqualTo(Path.of("h1.epub")));
    }

    @Test
    void delete_lastProjectOfABook_leavesNoProjectOfThatBook() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("a", "h1", "One", "en", "uk"));

        repository.delete("a");

        assertThat(repository.findByPair("h1", "en", "uk").data()).isEmpty();
        assertThat(repository.relocateBook("h1", Path.of("moved.epub")).data()).isEmpty();
    }

    @Test
    void glossary_personsEditThenRunsAddition_bothSurvive() {
        givenProjects("p1");
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(term("e1", "Hale"));
        glossary.update("p1", "Hale", held -> held.withTarget("Гейл"));

        checkpointPort().commit(additionOf(term("e2", "Milton")));

        assertThat(glossary.findByTerm("p1", "Hale").data())
                .hasValueSatisfying(e -> assertThat(e.target()).isEqualTo("Гейл"));
        assertThat(glossary.findByTerm("p1", "Milton").data()).isPresent();
    }

    @Test
    void glossary_runsAdditionThenPersonsEdit_bothSurvive() {
        givenProjects("p1");
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(term("e1", "Hale"));

        checkpointPort().commit(additionOf(term("e2", "Milton")));
        glossary.update("p1", "Hale", held -> held.withTarget("Гейл"));

        assertThat(glossary.all("p1").data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactly(tuple("Hale", "Гейл"), tuple("Milton", null));
    }

    private void storeWork(final String projectId) {
        segmentRepository().saveAll(projectId, List.of(pending(projectId, "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)));
        glossaryRepository()
                .add(new GlossaryEntry(
                        "g-" + projectId, projectId, "Hale", null, TermType.CHARACTER, Gender.MALE, false));
        tmRepository().put(new TmEntry("t-" + projectId, projectId, "h1", "c1", "Hello", "Привіт"));
        summaryRepository().save(new RollingSummary(projectId, null, "src", "tgt", 1, null, 0));
        lexiconRepository().put(LexiconEntry.of(projectId, "master"));
        assertThat(segmentRepository()
                        .countsByStatus(projectId, Set.of())
                        .data()
                        .pending())
                .isEqualTo(1);
    }

    private static GlossaryEntry term(final String id, final String text) {
        return new GlossaryEntry(id, "p1", text, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    private static ChunkCommit additionOf(final GlossaryEntry entry) {
        return new ChunkCommit("p1", List.of(), List.of(), List.of(entry), List.of());
    }

    private static Project withSource(final Project p, final Path source) {
        return new Project(
                p.id(),
                source,
                p.format(),
                p.contentHash(),
                p.brief(),
                p.name(),
                p.nameOrigin(),
                p.createdAt(),
                p.lastOpenedAt());
    }
}
