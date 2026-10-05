package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** Jobs whose recovery waits and watchdog cadence a test scripts. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TimedJobs {

    /**
     * A job paused on errors whose clock moves only when the timer or the test moves it, so hours of an outage or a
     * stall replay at once.
     */
    static TranslationJobImpl timedJob(
            final TestProject project,
            final ChatModel model,
            final Clock clock,
            final RecoveryTimer timer,
            final RunTicks ticks) {
        final TranslationJobImpl job = new TranslationJobImpl(
                project.documents(),
                new RunRequest(project.id(), ReviewMode.UNATTENDED),
                model,
                new ObjectMapper(),
                new PromptTemplates(),
                project.stores(),
                TranslationJobTestSupport.qualityLoop(),
                TranslationJobTestSupport.SPLITTER,
                ConsistencyPassFixture.over(project),
                clock,
                timer,
                ticks,
                BatchDrafter.NO_BATCHING);
        job.pauseAt(Set.of(PausePoint.ON_ERROR));
        return job;
    }
}
