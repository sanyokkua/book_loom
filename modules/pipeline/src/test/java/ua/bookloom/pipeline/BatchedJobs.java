package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.batch.BatchDrafter;
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
        return new TranslationJobImpl(
                project.documents(),
                request,
                model,
                new ObjectMapper(),
                new PromptTemplates(),
                project.stores(),
                TranslationJobTestSupport.qualityLoop(),
                TranslationJobTestSupport.SPLITTER,
                ConsistencyPassFixture.over(project),
                Clock.systemUTC(),
                RecoveryTimer.REAL,
                RunTicks.DAEMON,
                BatchDrafter.DEFAULT_INITIAL_SIZE);
    }
}
