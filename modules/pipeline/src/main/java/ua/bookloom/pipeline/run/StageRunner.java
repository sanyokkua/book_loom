package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.pipeline.proofread.ProofreadStage;

/**
 * The run's stages after preparation: Translate, then — once every chunk is decided, at every dial — Proofread, which
 * the final audit opens (design D2). A run that stops during Translate still counts the narrator's forms over what it
 * decided, with no audit and no Proofread; a run ends Completed only after its Proofread stage. The between-stages
 * pause already happened at the last decision, so Proofread starts without another boundary.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class StageRunner {

    private final ChunkRunner chunks;
    private final RunSettings settings;
    private final RunSinks sinks;
    private final NarratorStage narrator;
    private final ProofreadStage proofread;

    /**
     * Creates the stage sequence of one run.
     *
     * @param chunks the non-null runner of the translation stage
     * @param settings the non-null brief and review mode of the run
     * @param sinks the non-null places every decision and event goes
     * @param narrator the non-null count of the narrator's own forms, run here only for a run stopped during Translate
     * @param proofread the non-null last stage, run once every chunk is decided
     */
    public StageRunner(
            final ChunkRunner chunks,
            final RunSettings settings,
            final RunSinks sinks,
            final NarratorStage narrator,
            final ProofreadStage proofread) {
        this.chunks = Objects.requireNonNull(chunks, "chunks");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.narrator = Objects.requireNonNull(narrator, "narrator");
        this.proofread = Objects.requireNonNull(proofread, "proofread");
    }

    /**
     * Runs Translate and, once it completed, Proofread.
     *
     * @param work the non-null list of the segments to decide
     * @return completed once every stage ran, or how a boundary or a failure ended the run
     */
    public RunEnd run(final WorkList work) {
        Objects.requireNonNull(work, "work");
        log.debug("Translation job stage change stage={} project={}", JobStage.TRANSLATE, settings.projectId());
        sinks.emit().accept(new StageStarted(JobStage.TRANSLATE, work.currentTranslationProgress()));
        final RunEnd translated = chunks.run(work);
        log.debug("Translation stage ended project={} end={}", settings.projectId(), translated.state());
        return switch (translated.state()) {
            case COMPLETED -> proofread.run(work::proofreadProgress);
            case FAILED -> translated;
            case NEW, RUNNING, PAUSED, CANCELLED -> narratorSettled(translated);
        };
    }

    // A run stopped during Translate still counts the narrator's forms over what it decided, so the next run starts
    // from the gender they show; a storage error of the count is logged, and the run ends as it was stopped.
    private RunEnd narratorSettled(final RunEnd stopped) {
        final Optional<AppError> failed = narrator.settle();
        failed.ifPresent(error -> log.warn(
                "The narrator count failed to store after the run ended state={} code={}",
                stopped.state(),
                error.code()));
        return stopped;
    }
}
