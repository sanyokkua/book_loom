package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.PromptFrameRepository;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.PromptFrame;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;

/**
 * The storage rules a project and its book keep across adapters ({@code specs/local-storage/spec.md}): a name is
 * unique within one book ignoring case, one book is shared by its content, removing a project removes its work and
 * nothing else, and a person's glossary edit and a run's glossary addition never overwrite each other. Continues
 * {@link LexiconContractTest} in its own file by the project's class-length limit.
 */
public abstract class ProjectStorageContractTest extends GlossaryIndexTest {

    private static final String FRAME_HASH = "a1" + "0".repeat(62);

    protected abstract PromptFrameRepository promptFrameRepository();

    // Two segments of one run name the same frame; the run stores it once per segment it drafts, and one copy is kept.
    @Test
    void promptFrame_twoSegmentsOneRun_oneStoredFrame() {
        givenProjects("p1");
        final PromptFrame frame = frame(FRAME_HASH);
        segmentRepository()
                .saveAll(
                        "p1",
                        List.of(
                                withFrame(pending("p1", "ch1:0", "ch1", 0, SegmentKind.PARAGRAPH)),
                                withFrame(pending("p1", "ch1:1", "ch1", 1, SegmentKind.PARAGRAPH))));

        promptFrameRepository().save("p1", frame);
        promptFrameRepository().save("p1", frame);

        assertThat(promptFrameRepository().all("p1").data()).containsExactly(frame);
        assertThat(segmentRepository().all("p1").data())
                .allSatisfy(record -> assertThat(promptFrameRepository()
                                .find("p1", record.context().frameHash())
                                .data())
                        .contains(frame));
    }

    @Test
    void promptFrame_removeProject_frameGone() {
        final ProjectRepository repository = projectRepository();
        repository.save(project("p1", "h1", "One", "en", "uk"));
        repository.save(project("p2", "h1", "Two", "en", "de"));
        promptFrameRepository().save("p1", frame(FRAME_HASH));
        promptFrameRepository().save("p2", frame(FRAME_HASH));

        repository.delete("p1");

        assertThat(promptFrameRepository().find("p1", FRAME_HASH).data()).isEmpty();
        assertThat(promptFrameRepository().all("p1").data()).isEmpty();
        assertThat(promptFrameRepository().find("p2", FRAME_HASH).data()).isPresent();
    }

    @Test
    void promptFrame_unknownHash_answersOkAndEmpty() {
        givenProjects("p1");

        final Result<Optional<PromptFrame>> found = promptFrameRepository().find("p1", FRAME_HASH);

        assertThat(found.isOk()).isTrue();
        assertThat(found.data()).isEmpty();
    }

    private static PromptFrame frame(final String hash) {
        return new PromptFrame(
                hash,
                List.of(
                        new PromptSection(
                                "role", "", PromptSection.Origin.SYSTEM, List.of("Translate into Ukrainian.")),
                        new PromptSection("styleSheet", "[Style]", PromptSection.Origin.SYSTEM, List.of("Formal."))));
    }

    private static SegmentRecord withFrame(final SegmentRecord record) {
        return new SegmentRecord(
                record.projectId(),
                record.segmentId(),
                record.unitId(),
                record.ord(),
                record.kind(),
                record.status(),
                null,
                null,
                null,
                null,
                0.0,
                null,
                List.of(),
                record.path(),
                false,
                new ContextSnapshot(
                        FRAME_HASH,
                        List.of(new PromptSection(
                                "items", "[Items]", PromptSection.Origin.USER, List.of(record.segmentId()))),
                        List.of(),
                        false));
    }

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

    // IF the book title were not kept with the book, THEN a reopened window could not rebuild an automatic name.
    @Test
    void save_projectWithABookTitle_findAndAllReturnTheTitle() {
        final ProjectRepository repository = projectRepository();
        final Project base = project("a", "h1", "Earth Gravity · EN → UK", "en", "uk");
        repository.save(new Project(
                base.id(),
                base.source(),
                base.format(),
                base.contentHash(),
                base.brief(),
                base.name(),
                base.nameOrigin(),
                "Earth Gravity",
                base.createdAt(),
                base.lastOpenedAt()));

        assertThat(repository.find("a").data())
                .hasValueSatisfying(p -> assertThat(p.title()).isEqualTo("Earth Gravity"));
        assertThat(repository.all().data()).extracting(Project::title).containsExactly("Earth Gravity");
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
                .add(GlossaryEntry.of(
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
        return GlossaryEntry.of(id, "p1", text, null, TermType.CHARACTER, Gender.UNKNOWN, false);
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
                p.title(),
                p.createdAt(),
                p.lastOpenedAt());
    }
}
