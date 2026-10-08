package ua.bookloom.pipeline.prompt;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;

/**
 * The one seam every model call of a run goes through, so a call is announced, timed and redone in one place instead
 * of every call site reimplementing it (design D7/D8).
 */
@FunctionalInterface
public interface ModelCalls {

    /**
     * Sends one model call.
     *
     * @param kind which call this is
     * @param segmentId the segment the call is about, or null for a call that names no single segment
     * @param request the request to send
     * @return the model's reply, or the failure the call ended with
     */
    Result<ChatResponse> call(CallKind kind, @Nullable String segmentId, ChatRequest request);

    /**
     * Sends one model call about several segments, such as a reviewer call over a whole chunk, so its announcements name
     * every segment it is about.
     *
     * @param kind which call this is
     * @param segmentIds the segments the call is about, in document order; never null, possibly empty
     * @param request the request to send
     * @return the model's reply, or the failure the call ended with
     */
    default Result<ChatResponse> callAbout(
            final CallKind kind, final List<String> segmentIds, final ChatRequest request) {
        return call(kind, segmentIds.size() == 1 ? segmentIds.getFirst() : null, request);
    }

    /**
     * Sends one model call a person can inspect: a seam that shows calls announces it with the segments it is about,
     * its prompt part by part and its reply. A seam that shows nothing sends it as {@link #callAbout(CallKind, List,
     * ChatRequest)} does.
     *
     * @param kind which call this is
     * @param segmentIds the segments the call is about, in document order; never null, possibly empty
     * @param request the request to send
     * @param descriptor the non-null description built from the same values as the request
     * @return the model's reply, or the failure the call ended with
     */
    default Result<ChatResponse> callAbout(
            final CallKind kind,
            final List<String> segmentIds,
            final ChatRequest request,
            final CallDescriptor descriptor) {
        return callAbout(kind, segmentIds, request);
    }

    /**
     * Notes what became of a segment a shown call was about, so the call's snapshot says it. A seam that shows nothing
     * ignores it.
     *
     * @param note the non-null outcome
     */
    default void noted(final SegmentOutcomeNote note) {
        // Nothing shows calls.
    }

    /**
     * Announces what a step between calls has reached, such as a repair round starting. A seam that announces nothing
     * ignores it.
     *
     * @param event the non-null event
     */
    default void announce(final JobEvent event) {
        // Nothing listens.
    }

    /** The stages of a backward revision whose calls are counted before the first is sent. */
    enum Stage {
        /** One call per segment whose character's gender became known. */
        GENDER_RETRY,
        /** One call per repaired or flagged paragraph, checked against its neighbours. */
        NEIGHBOUR_CHECK
    }

    /**
     * Announces how many calls a stage is about to send at most, so a caller can show a determinate bar. A seam that
     * does not count ignores it.
     *
     * @param stage the stage about to start; non-null
     * @param calls the most calls it will send; zero or more
     */
    default void planned(final Stage stage, final int calls) {
        // Nothing counts.
    }
}
