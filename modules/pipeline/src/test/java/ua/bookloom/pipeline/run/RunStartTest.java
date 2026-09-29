package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.ProjectServiceImpl;

/** The refusals that stop a run before any model call, over a project imported through the real service. */
class RunStartTest {

    @TempDir
    private Path tempDir;

    private ProjectServiceImpl service;
    private RunStores stores;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        service = injector.getInstance(ProjectServiceImpl.class);
        stores = new RunStores(
                injector.getInstance(ProjectRepository.class),
                injector.getInstance(SegmentRepository.class),
                injector.getInstance(CheckpointPort.class),
                injector.getInstance(OpenProjects.class),
                injector.getInstance(RunRepository.class),
                injector.getInstance(GlossaryRepository.class));
        final Path book = Files.writeString(tempDir.resolve("Book.md"), "Hello.");
        projectId = Objects.requireNonNull(
                Objects.requireNonNull(service.importBook(book).data()).projectId());
    }

    // Narrowing the grammar would refuse one of these supported BCP-47-shaped values.
    @ParameterizedTest
    @CsvSource(
            value = {"uk,NULL", "eng,en-US", "zh-Hant,sl-rozaj-biske-1994"},
            nullValues = "NULL")
    void check_validLanguageSubtags_startsTheRun(final String target, @Nullable final String source) {
        briefWith(source, target);

        final Result<RunStart.Started> result = RunStart.check(stores, projectId);

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).extracting(started -> started.project().id()).isEqualTo(projectId);
    }

    // Dropping target-language validation would let path-like or malformed values into the output file name.
    @ParameterizedTest
    @ValueSource(strings = {"../x", "e", "engl", "en-", "en-a", "en-abcdefghi", "en_US"})
    void check_invalidTargetLanguage_returnsValidation(final String target) {
        briefWith("en", target);

        assertRefused(RunStart.check(stores, projectId), "This language code is not valid");
    }

    // Applying the grammar only to the target would accept a malformed source language.
    @ParameterizedTest
    @ValueSource(strings = {"../en", "e", "english", "en-", "en_uk"})
    void check_invalidSourceLanguage_returnsValidation(final String source) {
        briefWith(source, "uk");

        assertRefused(RunStart.check(stores, projectId), "This language code is not valid");
    }

    @Test
    void check_noTargetLanguage_returnsValidation() {
        briefWith("en", null);

        assertRefused(RunStart.check(stores, projectId), "No target language is chosen");
    }

    @Test
    void check_unknownProject_returnsValidation() {
        assertRefused(RunStart.check(stores, "missing"), "This project is not known");
    }

    // Starting on a closed book would read a document nothing holds any more.
    @Test
    void check_bookNoLongerOpen_returnsValidation() {
        briefWith("en", "uk");
        stores.openProjects().remove(projectId);

        assertRefused(RunStart.check(stores, projectId), "This project is not open");
    }

    private void briefWith(@Nullable final String source, @Nullable final String target) {
        final Project project = Objects.requireNonNull(
                        Objects.requireNonNull(stores.projects().find(projectId).data(), "find"))
                .orElseThrow();
        final BookBrief base = project.brief();
        final BookBrief brief = new BookBrief(
                source,
                target,
                base.genre(),
                base.register(),
                base.voiceEra(),
                base.audience(),
                base.names(),
                base.foreignPassages(),
                base.footnotes(),
                base.units(),
                base.balance(),
                base.alsoTranslate(),
                base.dial());
        service.updateBrief(projectId, brief);
    }

    private static void assertRefused(final Result<RunStart.Started> result, final String title) {
        assertThat(result.isErr()).isTrue();
        assertThat(result.error())
                .extracting(AppError::code, AppError::title)
                .containsExactly(ErrorCode.validation, title);
    }
}
