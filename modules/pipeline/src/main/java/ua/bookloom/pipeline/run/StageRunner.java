package ua.bookloom.pipeline.run;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.revision.ConsistencyPass;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/**
 * The run's stages after preparation: translation, then — only when the dial revises backwards and every segment was
 * decided — backward revision. The between-stages pause already happened at the last decision, so revision starts
 * without another boundary. Its revision calls are routed like a draft's: a pause or stop aborts or refuses the call
 * and is answered by the job's boundaries, so a pause asked for during the deterministic sweep waits for the next
 * model call and resumes by making it again.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class StageRunner {

    private final ChunkRunner chunks;
    private final ConsistencyPass revision;
    private final RunSettings settings;
    private final RunSinks sinks;
    private final ModelCalls calls;

    /**
     * Creates the stage sequence of one run.
     *
     * @param chunks the non-null runner of the translation stage
     * @param revision the non-null backward-revision pass
     * @param settings the non-null brief and review mode of the run
     * @param sinks the non-null places every decision and event goes
     * @param calls the non-null model-call seam of the run, the one the translation stage calls through
     */
    public StageRunner(
            final ChunkRunner chunks,
            final ConsistencyPass revision,
            final RunSettings settings,
            final RunSinks sinks,
            final ModelCalls calls) {
        this.chunks = Objects.requireNonNull(chunks, "chunks");
        this.revision = Objects.requireNonNull(revision, "revision");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /**
     * Runs translation and, on a dial that asks for it, backward revision.
     *
     * @param work the non-null list of the segments to decide
     * @return completed once every stage ran, or how a boundary or a failure ended the run
     */
    public RunEnd run(final WorkList work) {
        Objects.requireNonNull(work, "work");
        log.debug("Translation job stage change stage={} project={}", JobStage.TRANSLATE, settings.projectId());
        sinks.emit().accept(new StageStarted(JobStage.TRANSLATE, work.currentTranslationProgress()));
        final RunEnd translated = chunks.run(work);
        final boolean revises = settings.dial().backwardRevision();
        log.debug(
                "Translation stage ended project={} end={} dialRevisesBackwards={}",
                settings.projectId(),
                translated.state(),
                revises);
        if (translated.state() != JobState.COMPLETED || !revises) {
            return translated;
        }
        return revise(work);
    }

    // The pass reads the stored records, so the decided prefix is committed before it starts.
    private RunEnd revise(final WorkList work) {
        final Result<Integer> flushed = sinks.pending().flush();
        if (flushed.isErr()) {
            return RoutedCalls.failedBy(Objects.requireNonNull(flushed.error(), "error"));
        }
        log.debug("Translation job stage change stage={} project={}", JobStage.REVISE, settings.projectId());
        sinks.emit().accept(new StageStarted(JobStage.REVISE, work.revisionProgress()));
        final RoutedRevisionCalls routed = new RoutedRevisionCalls(new RoutedCalls(sinks.boundaries()), work);
        final Result<ConsistencyReport> revised = revision.run(settings.projectId(), routed);
        final RunEnd stopped = routed.stopped();
        if (stopped != null) {
            log.debug("Backward revision ended by the run's boundary end={}", stopped.state());
            return stopped;
        }
        if (revised.isErr()) {
            return RoutedCalls.failedBy(Objects.requireNonNull(revised.error(), "error"));
        }
        log.debug(
                "Backward revision stage done project={} changes={}",
                settings.projectId(),
                Objects.requireNonNull(revised.data(), "report").notes().size());
        return new RunEnd(JobState.COMPLETED, null);
    }

    /**
     * The revision pass's model seam: each call is routed until it answers, and a reply the pass reads as unreadable
     * — an empty completion or an overflowing context — goes back to the pass instead of flagging anything, since a
     * revision only ever leaves a segment as it was.
     */
    private final class RoutedRevisionCalls implements ModelCalls {

        private final RoutedCalls routed;
        private final WorkList work;
        // Written and read on the job thread only.
        private @Nullable RunEnd stopped;

        RoutedRevisionCalls(final RoutedCalls routed, final WorkList work) {
            this.routed = routed;
            this.work = work;
        }

        @Override
        public Result<ChatResponse> call(
                final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
            final Step<Result<ChatResponse>> step = routed.untilAnswered(
                    work::revisionProgress, segmentId, () -> keepUnreadable(calls.call(kind, segmentId, request)));
            return switch (step) {
                case Step.Done<Result<ChatResponse>>(final Result<ChatResponse> answer) -> answer;
                case Step.Stopped<Result<ChatResponse>>(final RunEnd end) -> {
                    stopped = end;
                    yield Result.err(AppError.of(
                            ErrorCode.cancelled,
                            "Backward revision stopped",
                            "The run ended while a revision call waited."));
                }
            };
        }

        @Nullable
        RunEnd stopped() {
            return stopped;
        }

        private static Result<Result<ChatResponse>> keepUnreadable(final Result<ChatResponse> answer) {
            final AppError error = answer.error();
            return error == null || PauseDecider.route(error.code()) == PauseDecider.Route.FLAG_AT_ONCE
                    ? Result.ok(answer)
                    : Result.err(error);
        }
    }
}
