package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** Jobs that draft in batches, as a run does; {@link TranslationJobTestSupport#job} drafts every segment alone. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchedJobs {

    /** A job that drafts in batches from the production first size, over the same project the other jobs use. */
    static TranslationJobImpl batchedJob(final TestProject project, final ChatModel model) {
        return batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED));
    }

    /** A job that drafts in batches, asked to run exactly {@code request}. */
    static TranslationJobImpl batchedJob(final TestProject project, final ChatModel model, final RunRequest request) {
        return batchedJob(project, model, request, BatchDrafter.DEFAULT_INITIAL_SIZE);
    }

    /** A job that drafts in batches that start at {@code firstBatchSize} items. */
    static TranslationJobImpl batchedJob(
            final TestProject project, final ChatModel model, final RunRequest request, final int firstBatchSize) {
        return batchedJob(project, model, request, firstBatchSize, new PromptTemplates());
    }

    /**
     * A job that drafts in batches with the templates an eval's variants built, starting at the run model's profile
     * size as the app does (a small model starts smaller than a large one).
     */
    static TranslationJobImpl batchedJob(
            final TestProject project,
            final ChatModel model,
            final RunRequest request,
            final PromptTemplates templates) {
        return batchedJob(project, model, request, RunPacing.FROM_PROFILE, templates);
    }

    private static TranslationJobImpl batchedJob(
            final TestProject project,
            final ChatModel model,
            final RunRequest request,
            final int firstBatchSize,
            final PromptTemplates templates) {
        return new TranslationJobImpl(
                request,
                new JobTools(project.documents(), model, new ObjectMapper(), templates),
                project.stores(),
                new RunChecks(
                        TranslationJobTestSupport.qualityLoop(),
                        TranslationJobTestSupport.SPLITTER,
                        ProofreadFixture.over(project),
                        WordValidator.none()),
                new RunPacing(
                        Clock.systemUTC(), new UuidIdSource(), RecoveryTimer.REAL, RunTicks.DAEMON, firstBatchSize),
                new RunLogFacts(CallLedger.none(), "unknown"));
    }
}
