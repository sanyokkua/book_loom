package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;

/**
 * One model call as a person can inspect it: the segments it is about, the prompt it sent part by part, where it
 * stands, the reply it got and what became of each segment. A segment's own context snapshot says what one segment
 * was meant to see; this says what one call actually sent, which for a batch or a reviewer call covers several
 * segments at once.
 *
 * @param callId the call's number within its run, from one; every snapshot of one call carries the same
 * @param kind what the call is for
 * @param label the name of the prompt the call was built from ({@code draft-batch-json}, {@code reviewer}), which a
 *     screen may word in its own language
 * @param position the chunk the call was made in, or null when the call names none
 * @param segments the segments the call is about, in document order; empty for a call about none
 * @param sections the filled parts of the prompt in the order they were sent, the system message's first
 * @param reply the model's reply as received, or null until the call is answered
 * @param state where the call stands
 * @param startedAt when the current attempt went out
 * @param elapsed how long the current attempt took, zero while it waits
 * @param attempt the current attempt, counted from one
 * @param maxAttempts how many attempts the call may make if each one stalls, at least {@code attempt}
 * @param timeout how long the current attempt may wait, or null when the model sets no bound of its own
 * @param usage the reply's token usage, reported or estimated, or null until it is answered
 * @param outcomes what became of the call's segments, in the order it was noted
 */
public record CallSnapshot(
        long callId,
        CallKind kind,
        String label,
        @Nullable ChunkPosition position,
        List<CallSegment> segments,
        List<PromptSection> sections,
        @Nullable String reply,
        CallState state,
        Instant startedAt,
        Duration elapsed,
        int attempt,
        int maxAttempts,
        @Nullable Duration timeout,
        @Nullable TokenUsage usage,
        List<SegmentOutcomeNote> outcomes) {

    /** Rejects a missing part or an impossible attempt, and copies the lists. */
    public CallSnapshot {
        if (callId < 1) {
            throw new IllegalArgumentException("callId must be positive: " + callId);
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(elapsed, "elapsed");
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        if (attempt < 1 || maxAttempts < attempt) {
            throw new IllegalArgumentException("attempt " + attempt + " of " + maxAttempts);
        }
    }

    /**
     * The snapshot of a call whose first attempt has just gone out.
     *
     * @param callId the call's number within its run, from one
     * @param kind the non-null kind
     * @param label the non-null prompt name
     * @param position the chunk, or null when the call names none
     * @param segments the non-null segments the call is about
     * @param sections the non-null prompt parts as sent
     * @param startedAt the non-null moment the attempt went out
     * @param attempt the attempt, counted from one
     * @param maxAttempts the most attempts the call may make, at least {@code attempt}
     * @param timeout the attempt's bound, or null when it has none
     * @return a waiting snapshot with no reply, no usage and no outcome
     */
    public static CallSnapshot waiting(
            final long callId,
            final CallKind kind,
            final String label,
            @Nullable final ChunkPosition position,
            final List<CallSegment> segments,
            final List<PromptSection> sections,
            final Instant startedAt,
            final int attempt,
            final int maxAttempts,
            @Nullable final Duration timeout) {
        return new CallSnapshot(
                callId,
                kind,
                label,
                position,
                segments,
                sections,
                null,
                CallState.WAITING,
                startedAt,
                Duration.ZERO,
                attempt,
                maxAttempts,
                timeout,
                null,
                List.of());
    }

    /**
     * The same call waiting again on a later attempt, after the one before it failed.
     *
     * @param at the non-null moment the attempt went out
     * @param number the attempt, counted from one
     * @param max the most attempts the call may make, at least {@code number}
     * @param bound the attempt's bound, or null when it has none
     * @return the call waiting, with no reply and no elapsed time
     */
    public CallSnapshot retrying(final Instant at, final int number, final int max, @Nullable final Duration bound) {
        return new CallSnapshot(
                callId,
                kind,
                label,
                position,
                segments,
                sections,
                null,
                CallState.WAITING,
                at,
                Duration.ZERO,
                number,
                max,
                bound,
                null,
                outcomes);
    }

    /**
     * The same call answered.
     *
     * @param text the non-null reply as received
     * @param tokens the reply's usage, or null when none is known
     * @param took the non-null time the answered attempt took
     * @return the call answered
     */
    public CallSnapshot answered(final String text, @Nullable final TokenUsage tokens, final Duration took) {
        Objects.requireNonNull(text, "text");
        return new CallSnapshot(
                callId,
                kind,
                label,
                position,
                segments,
                sections,
                text,
                CallState.ANSWERED,
                startedAt,
                took,
                attempt,
                maxAttempts,
                timeout,
                tokens,
                outcomes);
    }

    /**
     * The same call ended without a reply.
     *
     * @param end {@link CallState#FAILED} or {@link CallState#CANCELLED}
     * @param took the non-null time the attempt took
     * @return the call ended
     * @throws IllegalArgumentException if {@code end} is not a failure
     */
    public CallSnapshot ended(final CallState end, final Duration took) {
        if (end != CallState.FAILED && end != CallState.CANCELLED) {
            throw new IllegalArgumentException("a call ends failed or cancelled, not " + end);
        }
        return new CallSnapshot(
                callId,
                kind,
                label,
                position,
                segments,
                sections,
                null,
                end,
                startedAt,
                took,
                attempt,
                maxAttempts,
                timeout,
                null,
                outcomes);
    }

    /**
     * The same call with one more outcome noted.
     *
     * @param note the non-null outcome
     * @return the call with the note after the ones before it
     */
    public CallSnapshot withOutcome(final SegmentOutcomeNote note) {
        final List<SegmentOutcomeNote> more = new ArrayList<>(outcomes);
        more.add(Objects.requireNonNull(note, "note"));
        return new CallSnapshot(
                callId,
                kind,
                label,
                position,
                segments,
                sections,
                reply,
                state,
                startedAt,
                elapsed,
                attempt,
                maxAttempts,
                timeout,
                usage,
                more);
    }
}
