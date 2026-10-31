package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.AcceptReport;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The command line's {@code --review-names} accepts through the glossary's validated Accept all (translation-pipeline
 * "Accept only valid suggested names from the command line's names review"). Only the model's review is replaced, by
 * the five suggestions it would have left; the book's import, the scan, the checks and the store are the real ones.
 * Invented names only.
 */
class TranslateCommandNamesReviewTest {

    @TempDir
    private Path tempDir;

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    @Test
    void reviewNames_suggestionInObliqueForm_isRejectedAndCounted() throws IOException {
        final Injector core = core();
        final GlossaryService glossary = core.getInstance(GlossaryService.class);
        final String projectId = open(core);
        suggest(glossary, projectId, "Hale", "Гейл");
        suggest(glossary, projectId, "Velk", "Велк");
        suggest(glossary, projectId, "Mirel", "Мірел");
        suggest(glossary, projectId, "Orrin", "Оррін");
        suggest(glossary, projectId, "Valdor", "Валдором");
        final NameReview review = new NameReview(new ModelReviewLeavesTheSuggestions(glossary));

        final NameReview.Counts counts = review.review(projectId, noModel(), out());

        assertThat(counts.accepted()).isEqualTo(4);
        assertThat(counts.rejected()).isEqualTo(1);
        assertThat(printed.toString(StandardCharsets.UTF_8)).contains("names: ").contains("4 accepted, 1 rejected");
        assertThat(glossary.entries(projectId).data())
                .filteredOn(entry -> entry.term().equals("Valdor"))
                .extracting(GlossaryEntry::targetState)
                .containsExactly(TargetState.SUGGESTED);
    }

    private String open(final Injector core) throws IOException {
        final Path book = Files.writeString(
                tempDir.resolve("Book.txt"),
                "Hale and Velk met Mirel, Orrin and Valdor.\n\nThe road was long.\n",
                StandardCharsets.UTF_8);
        final TranslateArguments arguments =
                Objects.requireNonNull(TranslateArguments.parse(List.of(book.toString(), "--from", "en", "--to", "uk"))
                        .data());
        return Objects.requireNonNull(
                        core.getInstance(BookOpener.class).open(arguments).data())
                .projectId();
    }

    private static void suggest(
            final GlossaryService glossary, final String projectId, final String term, final String target) {
        glossary.add(GlossaryEntry.of(
                        UUID.nameUUIDFromBytes(term.getBytes(StandardCharsets.UTF_8))
                                .toString(),
                        projectId,
                        term,
                        null,
                        TermType.CHARACTER,
                        Gender.MALE,
                        false)
                .withSuggestedTarget(target));
    }

    private PrintStream out() {
        return new PrintStream(printed, true, StandardCharsets.UTF_8);
    }

    private static ChatModel noModel() {
        return request -> Result.err(AppError.of(ErrorCode.internal, "No model", "No call is expected."));
    }

    private Injector core() throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("test-logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(tempDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
        return Guice.createInjector(new CoreModules(startup, new InMemoryPersistenceModule()));
    }

    /** The real glossary service, except that the model's review answers with the glossary as it stands. */
    private record ModelReviewLeavesTheSuggestions(GlossaryService real) implements GlossaryService {

        @Override
        public Result<GlossaryReviewReport> review(
                final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
            return real.entries(projectId)
                    .map(entries -> new GlossaryReviewReport(
                            0,
                            0,
                            (int) entries.stream()
                                    .filter(GlossaryEntry::isSuggested)
                                    .count(),
                            entries));
        }

        @Override
        public Result<List<GlossaryEntry>> entries(final String projectId) {
            return real.entries(projectId);
        }

        @Override
        public Result<List<UnknownGender>> unknownGenders(final String projectId, final int minMentions) {
            return real.unknownGenders(projectId, minMentions);
        }

        @Override
        public Result<EntryChanges<GlossaryEntry>> scan(final String projectId) {
            return real.scan(projectId);
        }

        @Override
        public Result<EntryChanges<GlossaryEntry>> prescan(
                final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
            return real.prescan(projectId, model, progress);
        }

        @Override
        public Result<EntryChanges<GlossaryEntry>> translate(
                final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
            return real.translate(projectId, model, progress);
        }

        @Override
        public Result<AcceptReport> acceptSuggested(final String projectId) {
            return real.acceptSuggested(projectId);
        }

        @Override
        public Result<GlossaryEntry> add(final GlossaryEntry entry) {
            return real.add(entry);
        }

        @Override
        public Result<GlossaryEntry> update(final GlossaryEntry entry) {
            return real.update(entry);
        }

        @Override
        public Result<Boolean> remove(final String projectId, final String entryId) {
            return real.remove(projectId, entryId);
        }

        @Override
        public Result<GlossaryImportReport> importCsv(final String projectId, final Path source) {
            return real.importCsv(projectId, source);
        }

        @Override
        public Result<Path> exportCsv(final String projectId, final Path destination) {
            return real.exportCsv(projectId, destination);
        }
    }
}
