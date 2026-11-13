package ua.bookloom.pipeline.run;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RunRequest;

/**
 * Writes the run's start decisions to the always-on run log as counts and choices only. That log is the one file an
 * installed app keeps for every launch, so it must explain a night's run without a name, a rendering or a sentence of
 * the book, and without a provider's id or address.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class RunStartLog {

    /**
     * Writes the one start line of a run.
     *
     * @param request the non-null request the run was started with; only its model id and review mode are written
     * @param providerKind the kind of server the model runs on, or {@code unknown} when the run's provider is not one
     *     the registry knows (the pseudo model, a run with no selection)
     * @param window the context window in tokens the run sizes its calls against
     * @param dial the non-null quality the brief chose
     * @param batchSize the most segments the run's first batch may carry
     * @param prepared the non-null outcome of preparation, whose counts are written
     */
    public static void write(
            final RunRequest request,
            final String providerKind,
            final int window,
            final QualityDial dial,
            final int batchSize,
            final PrepStage.Prepared prepared) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(providerKind, "providerKind");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(prepared, "prepared");
        final ModelSelection selection = request.model();
        // Groups 5 to 7 of the pipeline redesign append their fields here (facts read, profile resolved, forms and
        // origins), each as a count or a choice.
        log.info(
                "Run start model={} providerKind={} window={} dial={} batch={} mode={} names pass entries={}"
                        + " proposed={}",
                selection == null ? request.modelName() : selection.modelId(),
                providerKind,
                window,
                dial,
                batchSize,
                request.mode(),
                prepared.entries(),
                prepared.proposed());
    }
}
