package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CountingIdSource;
import ua.bookloom.pipeline.TestBooks;

/** Project names, the one-project-per-pair rule and renaming, over the real documents and in-memory repositories. */
class ProjectServiceNamesTest {

    private static final List<List<String>> CHAPTERS =
            List.of(List.of("It was a dark night."), List.of("The rain kept falling."));
    private static final String TITLE = "Earth Gravity: A Novel of Velk";

    @TempDir
    private Path tempDir;

    private CountingIdSource ids;
    private ProjectService service;
    private ProjectRepository projects;
    private SegmentRepository segments;

    @BeforeEach
    void setUp() {
        ids = new CountingIdSource();
        final Injector injector =
                Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule(), binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(ids);
                });
        service = injector.getInstance(ProjectServiceImpl.class);
        projects = injector.getInstance(ProjectRepository.class);
        segments = injector.getInstance(SegmentRepository.class);
    }

    @Test
    void create_bookWithAColonTitle_namesTheProjectFromTheTitleBeforeTheColon() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));

        assertThat(stored(id).name()).isEqualTo("Earth Gravity · EN → …");
        assertThat(stored(id).nameOrigin()).isEqualTo(NameOrigin.AUTO);
        assertThat(stored(id).title()).isEqualTo("Earth Gravity");
    }

    // IF the metadata names no title, THEN the name starts with the file name without its extension.
    @Test
    void create_bookWithoutATitle_namesTheProjectFromTheFileName() {
        final String id = createOk(titled("earth-gravity.epub", ""));

        assertThat(stored(id).name()).isEqualTo("earth-gravity · EN → …");
        assertThat(stored(id).title()).isEqualTo("earth-gravity");
    }

    @Test
    void create_twoDifferentBooksWithTheSameTitle_bothKeepTheNaturalName() {
        final String first = createOk(titled("one.epub", TITLE));
        final String second = createOk(TestBooks.epubWithBookTitle(
                tempDir.resolve("two.epub"), List.of(List.of("Another text altogether.")), TITLE));

        assertThat(second).isNotEqualTo(first);
        assertThat(stored(first).name()).isEqualTo("Earth Gravity · EN → …");
        assertThat(stored(second).name()).isEqualTo("Earth Gravity · EN → …");
    }

    // IF nothing has been translated into a target yet, THEN importing the book again does not add a second project.
    @Test
    void create_twiceWithNoTargetChosen_reusesTheProjectAndTheId() {
        final Path book = titled("earth-gravity.epub", TITLE);
        final String first = createOk(book);

        final String second = createOk(book);

        assertThat(second).isEqualTo(first);
        assertThat(ids.issued()).isEqualTo(1);
        assertThat(projects.all().data()).hasSize(1);
    }

    // IF the first project already has a target, THEN a new import is a new project and keeps the earlier decisions.
    @Test
    void create_afterTheTargetIsSet_makesASecondProjectAndKeepsTheFirstOnesDecisions() {
        final Path book = titled("earth-gravity.epub", TITLE);
        final String first = createOk(book);
        saved(service.updateBrief(first, briefTo("uk")));
        final SegmentRecord firstRecord = records(first).get(0);
        segments.update(first, firstRecord.segmentId(), record -> record.withStatus(SegmentStatus.ACCEPTED));

        final String second = createOk(book);

        assertThat(second).isNotEqualTo(first);
        assertThat(ids.issued()).isEqualTo(2);
        assertThat(records(first).get(0).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(records(second)).allSatisfy(r -> assertThat(r.status()).isEqualTo(SegmentStatus.PENDING));
        assertThat(stored(first).name()).isEqualTo("Earth Gravity · EN → UK");
        assertThat(stored(second).name()).isEqualTo("Earth Gravity · EN → …");
    }

    @Test
    void updateBrief_automaticNameAndTargetChanged_nameFollowsTheLanguages() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));
        saved(service.updateBrief(id, briefTo("uk")));
        assertThat(stored(id).name()).isEqualTo("Earth Gravity · EN → UK");

        final Project changed = saved(service.updateBrief(id, briefTo("de")));

        assertThat(changed.name()).isEqualTo("Earth Gravity · EN → DE");
        assertThat(stored(id).name()).isEqualTo("Earth Gravity · EN → DE");
        assertThat(stored(id).nameOrigin()).isEqualTo(NameOrigin.AUTO);
    }

    @Test
    void updateBrief_typedNameAndTargetChanged_nameStays() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));
        saved(service.rename(id, "Earth Gravity with Max"));

        final Project changed = saved(service.updateBrief(id, briefTo("de")));

        assertThat(changed.name()).isEqualTo("Earth Gravity with Max");
        assertThat(changed.nameOrigin()).isEqualTo(NameOrigin.USER);
    }

    // IF the pair is already translated by another project of the book, THEN the brief is not stored.
    @Test
    void updateBrief_pairUsedByAnotherProjectOfTheBook_answersPairInUseAndStoresNothing() {
        final Path book = titled("earth-gravity.epub", TITLE);
        final String first = createOk(book);
        saved(service.updateBrief(first, briefTo("uk")));
        final String second = createOk(book);

        final ProjectChange change = changeOf(service.updateBrief(second, briefTo("uk")));

        assertThat(change).isInstanceOf(ProjectChange.PairInUse.class);
        assertThat(((ProjectChange.PairInUse) change).existing().id()).isEqualTo(first);
        assertThat(stored(second).brief().targetLanguage()).isNull();
        assertThat(stored(second).name()).isEqualTo("Earth Gravity · EN → …");
    }

    @Test
    void updateBrief_namedSecondProjectOnAUsedPair_isSaved() {
        final Path book = titled("earth-gravity.epub", TITLE);
        final String first = createOk(book);
        saved(service.updateBrief(first, briefTo("uk")));
        final String second = createOk(book);
        saved(service.rename(second, "Earth Gravity · 26b"));

        final Project changed = saved(service.updateBrief(second, briefTo("uk")));

        assertThat(changed.name()).isEqualTo("Earth Gravity · 26b");
        assertThat(stored(second).brief().targetLanguage()).isEqualTo("uk");
    }

    // IF an older store already holds two projects on one pair, THEN a save that touches neither pair nor name goes
    // through.
    @Test
    void updateBrief_unrelatedChangeWhileTheBookHoldsAClashingPair_isNeverBlocked() {
        final Path book = titled("earth-gravity.epub", TITLE);
        final String first = createOk(book);
        saved(service.updateBrief(first, briefTo("uk")));
        final Project twin = stored(first);
        final Project seeded = new Project(
                "seeded",
                twin.source(),
                twin.format(),
                twin.contentHash(),
                twin.brief(),
                "Earth Gravity · seeded",
                NameOrigin.AUTO,
                twin.title(),
                twin.createdAt(),
                twin.lastOpenedAt());
        assertThat(projects.save(seeded).isOk()).isTrue();

        final Project changed = saved(service.updateBrief(first, twin.brief().withLanguages("en", "uk")));

        assertThat(changed.id()).isEqualTo(first);
        assertThat(changed.name()).isEqualTo("Earth Gravity · EN → UK");
    }

    @Test
    void rename_typedName_isStoredAsTheUsersOwn() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));

        final Project renamed = saved(service.rename(id, "  Earth Gravity for Mum "));

        assertThat(renamed.name()).isEqualTo("Earth Gravity for Mum");
        assertThat(stored(id).name()).isEqualTo("Earth Gravity for Mum");
        assertThat(stored(id).nameOrigin()).isEqualTo(NameOrigin.USER);
    }

    // IF another project of the same book has the name in another letter case, THEN the rename is refused.
    @Test
    void rename_nameOfAnotherProjectOfTheBookInOtherCase_answersNameInUseAndKeepsTheName() {
        final Path book = titled("earth-gravity.epub", TITLE);
        final String first = createOk(book);
        saved(service.updateBrief(first, briefTo("uk")));
        saved(service.rename(first, "Земне тяжіння"));
        final String second = createOk(book);

        final ProjectChange change = changeOf(service.rename(second, "ЗЕМНЕ ТЯЖІННЯ"));

        assertThat(change).isInstanceOf(ProjectChange.NameInUse.class);
        assertThat(((ProjectChange.NameInUse) change).holder().id()).isEqualTo(first);
        assertThat(stored(second).name()).isEqualTo("Earth Gravity · EN → …");
    }

    @Test
    void rename_nameOfAProjectOfAnotherBook_isSaved() {
        final String first = createOk(titled("one.epub", TITLE));
        saved(service.rename(first, "Earth Gravity for Mum"));
        final String second = createOk(TestBooks.epubWithBookTitle(
                tempDir.resolve("two.epub"), List.of(List.of("Another text altogether.")), TITLE));

        final Project renamed = saved(service.rename(second, "Earth Gravity for Mum"));

        assertThat(renamed.name()).isEqualTo("Earth Gravity for Mum");
    }

    @Test
    void rename_blankName_answersValidationAndKeepsTheName() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));

        final Result<ProjectChange> blank = service.rename(id, "   ");

        assertThat(Objects.requireNonNull(blank.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(id).name()).isEqualTo("Earth Gravity · EN → …");
    }

    // IF any typed text were stored, THEN a name could be too long to show or hold a character no file name accepts.
    @ParameterizedTest
    @ValueSource(strings = {"Earth/Gravity", "Earth\\Gravity", "Earth\tGravity", "Earth\u0007Gravity"})
    void rename_nameWithASeparatorOrAControlCharacter_answersValidationAndKeepsTheName(final String typed) {
        final String id = createOk(titled("earth-gravity.epub", TITLE));

        final Result<ProjectChange> refused = service.rename(id, typed);

        assertThat(Objects.requireNonNull(refused.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(stored(id).name()).isEqualTo("Earth Gravity · EN → …");
    }

    @Test
    void rename_nameLongerThanTheLimit_answersValidationAndKeepsTheName() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));

        final Result<ProjectChange> refused = service.rename(id, "x".repeat(Project.MAX_NAME_LENGTH + 1));

        assertThat(Objects.requireNonNull(refused.error()).message()).contains("120");
        assertThat(stored(id).name()).isEqualTo("Earth Gravity · EN → …");
    }

    @Test
    void rename_nameAtTheLimit_isStored() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));

        final Project renamed = saved(service.rename(id, "x".repeat(Project.MAX_NAME_LENGTH)));

        assertThat(renamed.name()).hasSize(120);
    }

    @Test
    void rename_unknownProject_answersValidation() {
        assertThat(Objects.requireNonNull(service.rename("missing", "Name").error())
                        .code())
                .isEqualTo(ErrorCode.validation);
        assertThat(Objects.requireNonNull(service.rename("missing", null).error())
                        .code())
                .isEqualTo(ErrorCode.validation);
    }

    @Test
    void rename_nullWhenNoOtherProjectHoldsThePair_returnsToTheAutomaticName() {
        final String id = createOk(titled("earth-gravity.epub", TITLE));
        saved(service.updateBrief(id, briefTo("de")));
        saved(service.rename(id, "Earth Gravity with Max"));

        final Project back = saved(service.rename(id, null));

        assertThat(back.name()).isEqualTo("Earth Gravity · EN → DE");
        assertThat(back.nameOrigin()).isEqualTo(NameOrigin.AUTO);
    }

    // IF another project of the book translates the same pair, THEN "use the automatic name again" is refused until it
    // goes.
    @Test
    void rename_nullWhileAnotherProjectHoldsThePair_isRefusedThenAcceptedOnceItIsGone() {
        final Path book = titled("earth-gravity.epub", TITLE);
        final String first = createOk(book);
        saved(service.updateBrief(first, briefTo("uk")));
        final String second = createOk(book);
        saved(service.rename(second, "Earth Gravity · 26b"));
        saved(service.updateBrief(second, briefTo("uk")));

        final ProjectChange refused = changeOf(service.rename(second, null));

        assertThat(refused).isInstanceOf(ProjectChange.PairInUse.class);
        assertThat(stored(second).name()).isEqualTo("Earth Gravity · 26b");
        assertThat(projects.delete(first).isOk()).isTrue();

        final Project accepted = saved(service.rename(second, null));

        assertThat(accepted.name()).isEqualTo("Earth Gravity · EN → UK");
        assertThat(accepted.nameOrigin()).isEqualTo(NameOrigin.AUTO);
    }

    private Path titled(final String fileName, final String bookTitle) {
        return TestBooks.epubWithBookTitle(tempDir.resolve(fileName), CHAPTERS, bookTitle);
    }

    private String createOk(final Path book) {
        final Result<ImportedBook> result = service.create(book);
        assertThat(result.isOk()).as("create result %s", result.error()).isTrue();
        return Objects.requireNonNull(Objects.requireNonNull(result.data()).projectId());
    }

    private static BookBrief briefTo(final String target) {
        return BookBrief.defaults("en").withLanguages("en", target);
    }

    private static ProjectChange changeOf(final Result<ProjectChange> result) {
        assertThat(result.isOk()).as("result %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private static Project saved(final Result<ProjectChange> result) {
        final ProjectChange change = changeOf(result);
        assertThat(change).isInstanceOf(ProjectChange.Saved.class);
        return ((ProjectChange.Saved) change).project();
    }

    private Project stored(final String id) {
        return Objects.requireNonNull(projects.find(id).data()).orElseThrow();
    }

    private List<SegmentRecord> records(final String id) {
        return Objects.requireNonNull(segments.all(id).data());
    }
}
