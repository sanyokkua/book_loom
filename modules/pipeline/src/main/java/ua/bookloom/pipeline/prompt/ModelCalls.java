package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallItem;
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
     * Shows what a batch reply held for each expected id, on the call that was sent. A seam that shows nothing ignores
     * it.
     *
     * @param segmentIds the segments the batch call was about, in item order; never null
     * @param items the non-null items the reply was read as, one per expected id then any extra id
     */
    default void parsed(final List<String> segmentIds, final List<CallItem> items) {
        // Nothing shows calls.
    }

    /**
     * Shows what the reply of the call just answered was read as, for a call about no segment: each line it asked about
     * with the parsed answer for it. A seam that shows nothing ignores it.
     *
     * @param items the non-null items, one per line asked, in the order asked
     */
    default void read(final List<CallItem> items) {
        parsed(List.of(), items);
    }

    /**
     * Shows each line a call about no segment asked with the parsed answer for it; a line the reply gave no answer for
     * stays as asked. A seam that shows nothing ignores it.
     *
     * @param answers each line asked, in order, with its answer or null when the reply gave none
     */
    default void answered(final Map<String, @Nullable String> answers) {
        read(answers.entrySet().stream()
                .map(line -> line.getValue() == null
                        ? CallItem.asked(line.getKey())
                        : CallItem.answered(line.getKey(), line.getValue()))
                .toList());
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

    /**
     * The steps this seam announces. A seam that announces no steps returns a silent announcer.
     *
     * @return the non-null announcer
     */
    default StepAnnouncer steps() {
        return StepAnnouncer.silent();
    }
}
