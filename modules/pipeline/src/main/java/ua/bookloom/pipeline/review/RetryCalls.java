package ua.bookloom.pipeline.review;

import java.time.Clock;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * The call seam of a retry: the run's own, over the retry's model, so each call is numbered, timed and described like
 * a run's call and a screen can show what the retry sent and what came back.
 */
// Checkstyle parses source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RetryCalls {

    static ModelCalls over(
            final ChatModel model,
            final Consumer<JobEvent> progress,
            final Clock clock,
            final String targetLanguage,
            final Document document) {
        final Map<String, SegmentLocator> locators = SegmentLocators.of(document);
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                progress,
                clock,
                targetLanguage,
                ContextBudget.DEFAULT_WINDOW,
                locators);
    }
}
