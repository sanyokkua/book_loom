package ua.bookloom.pipeline.run;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.prompt.CallDescriptor;

/**
 * The run's described calls as a person inspects them: each is numbered when its first attempt goes out and announced
 * again, under the same number, as it waits, ends and gathers what became of its segments. The draft call that last
 * drafted a segment receives that segment's outcomes until its decision. Only the latest calls are kept, so a long
 * run holds a bounded number of prompts.
 *
 * <p>Used from the job thread only. The snapshots carry book text, which is logged at TRACE only.
 */
@Slf4j
final class CallSnapshots {

    // Enough for the calls of a few chunks; an older call's outcome is no longer shown.
    private static final int KEPT_CALLS = 64;
    private static final int KEPT_SEGMENTS = 512;
    private static final Set<CallKind> DRAFTS =
            EnumSet.of(CallKind.DRAFT, CallKind.STRUCTURAL_REPAIR, CallKind.PLACEHOLDER_REPAIR);

    private final Consumer<JobEvent> announce;
    private final Map<String, SegmentLocator> locators;
    private final Map<Long, CallSnapshot> shown = latest(KEPT_CALLS);
    private final Map<String, Long> draftedBy = latest(KEPT_SEGMENTS);
    private long lastId;

    CallSnapshots(final Consumer<JobEvent> announce, final Map<String, SegmentLocator> locators) {
        this.announce = Objects.requireNonNull(announce, "announce");
        this.locators = Map.copyOf(Objects.requireNonNull(locators, "locators"));
    }

    /**
     * Starts following one call.
     *
     * @return the call's tracker, or null for a call no descriptor describes, which is not shown
     */
    @Nullable
    Shown open(final CallKind kind, final List<String> segmentIds, @Nullable final CallDescriptor descriptor) {
        log.debug("Call snapshot kind={} segmentIds={} described={}", kind, segmentIds, descriptor != null);
        return descriptor == null ? null : new Shown(kind, segmentIds, descriptor);
    }

    /** Adds an outcome to the draft call that last drafted its segment; a decision ends that segment's following. */
    void noted(final SegmentOutcomeNote note) {
        Objects.requireNonNull(note, "note");
        final Long callId = note.isDecision() ? draftedBy.remove(note.segmentId()) : draftedBy.get(note.segmentId());
        final CallSnapshot call = callId == null ? null : shown.get(callId);
        log.debug(
                "Outcome noted segmentId={} kind={} detail={} callId={} shown={}",
                note.segmentId(),
                note.kind(),
                note.detail(),
                callId,
                call != null);
        if (call != null) {
            publish(call.withOutcome(note));
        }
    }

    private void publish(final CallSnapshot snapshot) {
        shown.put(snapshot.callId(), snapshot);
        log.debug(
                "Sending CallSnapshotUpdated callId={} kind={} state={} attempt={} segments={} sections={} outcomes={}",
                snapshot.callId(),
                snapshot.kind(),
                snapshot.state(),
                snapshot.attempt(),
                snapshot.segments().size(),
                snapshot.sections().size(),
                snapshot.outcomes().size());
        if (log.isTraceEnabled()) {
            log.trace("Call snapshot callId={} reply={}", snapshot.callId(), snapshot.reply());
        }
        announce.accept(new CallSnapshotUpdated(snapshot));
    }

    private List<CallSegment> segmentsOf(final List<String> ids, final List<String> sources) {
        final List<CallSegment> segments = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            final String id = ids.get(index);
            final SegmentLocator locator = locators.get(id);
            segments.add(new CallSegment(
                    id, locator == null ? id : locator.text(), index < sources.size() ? sources.get(index) : ""));
        }
        return segments;
    }

    private static <K, V> Map<K, V> latest(final int kept) {
        return new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
                return size() > kept;
            }
        };
    }

    private static CallState endOf(final ErrorCode code) {
        return code == ErrorCode.cancelled ? CallState.CANCELLED : CallState.FAILED;
    }

    /** One shown call, followed attempt by attempt. */
    final class Shown {

        private final CallKind kind;
        private final List<String> segmentIds;
        private final CallDescriptor descriptor;
        private @Nullable CallSnapshot current;

        private Shown(final CallKind kind, final List<String> segmentIds, final CallDescriptor descriptor) {
            this.kind = kind;
            this.segmentIds = List.copyOf(segmentIds);
            this.descriptor = descriptor;
        }

        void started(final CallAttempt attempt, final Instant at) {
            final CallSnapshot before = current;
            current = before == null
                    ? CallSnapshot.waiting(
                            ++lastId,
                            kind,
                            descriptor.label(),
                            descriptor.position(),
                            segmentsOf(segmentIds, descriptor.sources()),
                            descriptor.sections().get(),
                            at,
                            attempt.number(),
                            attempt.maxAttempts(),
                            attempt.timeout())
                    : before.retrying(at, attempt.number(), attempt.maxAttempts(), attempt.timeout());
            publish(current);
        }

        void failed(final ErrorCode code, final Duration elapsed) {
            end(endOf(code), elapsed);
        }

        void answered(final String reply, @Nullable final TokenUsage usage, final Duration elapsed) {
            final CallSnapshot before = current;
            if (before == null) {
                return;
            }
            current = before.answered(reply, usage, elapsed);
            if (DRAFTS.contains(kind)) {
                segmentIds.forEach(id -> draftedBy.put(id, before.callId()));
            }
            publish(current);
        }

        // The call's own answer is the last word: a cancelled attempt the run turned into a timeout failed.
        void ended(final ErrorCode code) {
            final CallSnapshot before = current;
            if (before != null && before.state() != CallState.ANSWERED) {
                end(endOf(code), before.elapsed());
            }
        }

        private void end(final CallState state, final Duration elapsed) {
            final CallSnapshot before = current;
            if (before == null || before.state() == state) {
                return;
            }
            current = before.ended(state, elapsed);
            publish(current);
        }
    }
}
