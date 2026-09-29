package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.ReuseCheck;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.memory.TranslationMemory;

/**
 * The run's side of the translation memory: before a segment is drafted, a context-matched entry is checked at once
 * and, passing, stands in for the draft; every accepted segment writes an entry with its commit. The memory records
 * what the run accepted, never a person's edit, so the entry is built from the machine's masked target.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class MemoryReuse {

    private final TranslationMemory memory;
    private final Map<String, SegmentLocator> locators;

    /**
     * Creates the memory side of one run.
     *
     * @param memory the non-null memory of the run's project
     * @param locators the non-null locator of every segment of the opened book, which names a reuse when it is
     *     announced
     */
    MemoryReuse(final TranslationMemory memory, final Map<String, SegmentLocator> locators) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.locators = Map.copyOf(Objects.requireNonNull(locators, "locators"));
    }

    /**
     * Looks a segment up and checks a context match at once: its protected spans, the chunk's gate, every check and
     * τ. A refused match is discarded, so the draft is offered only the hints and suggestions.
     *
     * @param segment the segment about to be drafted
     * @param unitSegments every segment of its unit, in document order
     * @param mask the segment's protected mask for the chunk
     * @param gate the chunk's gate
     * @param loop the chunk's loop settings, whose review mode sets τ
     * @return what the memory offers the draft, and the reuse when one passed
     */
    Offer offer(
            final Segment segment,
            final List<Segment> unitSegments,
            final ProtectedMask mask,
            final GateFunction gate,
            final LoopSettings loop) {
        final TmLookup lookup = memory.lookup(segment, unitSegments);
        final TmEntry match = lookup.reuse();
        if (match == null) {
            logDecision(segment, lookup, "none", null);
            return new Offer(lookup, null);
        }
        final Result<DraftOutcome.Reused> checked = ProtectedSpans.checkRestored(match.targetInner(), mask)
                .flatMap(remasked ->
                        ReuseCheck.check(segment, mask.maskedText(), mask.presentLocked(), remasked, gate, loop));
        final AppError refusal = checked.error();
        if (refusal != null) {
            logDecision(segment, lookup, "discarded", refusal);
            return new Offer(new TmLookup(null, lookup.hints(), lookup.suggestions()), null);
        }
        final DraftOutcome.Reused reused = Objects.requireNonNull(checked.data(), "reused");
        logDecision(segment, lookup, "accepted", null);
        if (log.isTraceEnabled()) {
            log.trace("Reused target segmentId={} target={}", segment.id(), reused.maskedTarget());
        }
        return new Offer(lookup, reused);
    }

    /**
     * The memory entry a decided record's commit writes.
     *
     * @param record the decided record
     * @param segment its segment
     * @param unitSegments every segment of its unit, in document order
     * @return the entry of an accepted record — a reuse's replaces the identical one — or {@code null} for any other
     */
    @Nullable
    TmEntry entryFor(final SegmentRecord record, final Segment segment, final List<Segment> unitSegments) {
        final String maskedTarget = record.maskedMachineTarget();
        if (record.status() != SegmentStatus.ACCEPTED || maskedTarget == null) {
            log.debug("No memory entry segmentId={} status={}", record.segmentId(), record.status());
            return null;
        }
        return memory.entryFor(segment, unitSegments, maskedTarget);
    }

    /**
     * The announcement of an accepted reuse, labelled with the segment's locator.
     *
     * @param segmentId the reused segment's id, one of the opened book's
     * @return the event to send
     */
    MemoryUpdated announced(final String segmentId) {
        final SegmentLocator locator =
                Objects.requireNonNull(locators.get(segmentId), () -> "no locator for " + segmentId);
        log.debug("Sending MemoryUpdated kind={} segmentId={} label={}", MemoryKind.TM, segmentId, locator.text());
        return new MemoryUpdated(MemoryKind.TM, locator.text());
    }

    private static void logDecision(
            final Segment segment, final TmLookup lookup, final String decision, @Nullable final AppError refusal) {
        log.debug(
                "Memory decision segmentId={} reuseFound={} decision={} refusedCode={} refusedBy={} hints={}"
                        + " suggestions={}",
                segment.id(),
                lookup.reuse() != null,
                decision,
                refusal == null ? null : refusal.code(),
                refusal == null ? null : refusal.details(),
                lookup.hints().size(),
                lookup.suggestions().size());
    }

    /**
     * What the memory offers one segment.
     *
     * @param lookup the hints and suggestions a draft is offered, and a passing match, which its snapshot records
     * @param reused the reuse that stands in for the draft, or {@code null} when the segment is drafted
     */
    record Offer(TmLookup lookup, DraftOutcome.@Nullable Reused reused) {}
}
