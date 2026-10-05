package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** Covers the public engine boundary: the job it creates runs over the stored project it is asked for. */
class TranslationEngineImplTest {

    @TempDir
    private Path tempDir;

    // Removing the owning-module binding would make resolving the public port fail.
    @Test
    void injector_pipelineModule_resolvesTranslationEngineImpl() {
        final TranslationEngine engine = Guice.createInjector(
                        new DocumentModule(), new PersistenceModule(), new PipelineModule(), new ReviewModeTestModule())
                .getInstance(TranslationEngine.class);

        assertThat(engine).isInstanceOf(TranslationEngineImpl.class);
    }

    // Checking the project when the job is created would turn an unknown project into an engine failure.
    @Test
    void newJob_unknownProject_returnsJobThatRefusesToRun() {
        final ScriptedChatModel model = new ScriptedChatModel();
        final TestProject project = project(book("One."), brief("en", "uk"));

        final Result<TranslationJob> created =
                engine(project).newJob(new RunRequest("missing", ReviewMode.UNATTENDED), model);

        final Result<JobReport> run = dataOf(created).run();
        assertThat(run.isErr()).isTrue();
        assertThat(errorOf(run).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // Losing the job's one-run claim would translate the same project twice through one public job.
    @Test
    void run_secondInvocation_returnsValidation() {
        final TestProject project = project(book("One."), brief("en", "uk"));
        final TranslationJob job = dataOf(engine(project).newJob(request(project), answers("ONE.")));

        final Result<JobReport> first = job.run();
        final Result<JobReport> second = job.run();

        assertThat(reportOf(first).end()).isEqualTo(JobState.COMPLETED);
        assertThat(second.isErr()).isTrue();
        assertThat(errorOf(second).code()).isEqualTo(ErrorCode.validation);
    }

    // Returning a boundary error instead of a failed report would break the public engine contract's partial result.
    @Test
    void run_modelFailureThroughPublicEntrypoint_returnsFailedReport() {
        final AppError unavailable = AppError.of(ErrorCode.unreachable, "Unavailable", "The model is unavailable.");
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.err(unavailable));
        final TestProject project = project(book("One."), brief("en", "uk"));
        final TranslationJob job = dataOf(engine(project).newJob(request(project), model));

        final Result<JobReport> result = job.run();

        assertThat(result.isOk()).isTrue();
        assertThat(reportOf(result).end()).isEqualTo(JobState.FAILED);
        assertThat(Objects.requireNonNull(reportOf(result).error(), "report error")
                        .code())
                .isEqualTo(ErrorCode.unreachable);
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // Treating a model cancellation as failure would expose the wrong terminal state through the public engine.
    @Test
    void run_modelCancellationThroughPublicEntrypoint_returnsCancelledReport() {
        final AppError cancelled = AppError.of(ErrorCode.cancelled, "Cancelled", "The request was cancelled.");
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.err(cancelled));
        final TestProject project = project(book("One."), brief("en", "uk"));
        final TranslationJob job = dataOf(engine(project).newJob(request(project), model));

        final Result<JobReport> result = job.run();

        assertThat(result.isOk()).isTrue();
        assertThat(reportOf(result).end()).isEqualTo(JobState.CANCELLED);
        assertThat(reportOf(result).error()).isNull();
    }

    private Path book(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static RunRequest request(final TestProject project) {
        return new RunRequest(project.id(), ReviewMode.UNATTENDED);
    }

    private static TranslationEngine engine(final TestProject project) {
        return new TranslationEngineImpl(
                project.documents(),
                new ObjectMapper(),
                new PromptTemplates(),
                project.stores().projects(),
                project.stores().segments(),
                project.stores().checkpoint(),
                project.stores().openProjects(),
                project.stores().runs(),
                project.stores().glossary(),
                project.stores().tm(),
                project.stores().summaries(),
                project.stores().lexicon(),
                TranslationJobTestSupport.qualityLoop(),
                Guice.createInjector(new DocumentModule()).getInstance(SentenceSplitter.class),
                ConsistencyPassFixture.over(project),
                Clock.systemUTC(),
                RecoveryTimer.REAL);
    }

    private static ScriptedChatModel answers(final String reply) {
        return new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(reply), FinishReason.STOP)));
    }

    private static JobReport reportOf(final Result<JobReport> result) {
        return Objects.requireNonNull(result.data(), "job report");
    }

    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), "result data");
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "result error");
    }
}
