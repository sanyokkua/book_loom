package ua.bookloom.pipeline.run;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.ReuseCheck;
import ua.bookloom.pipeline.heal.VerbatimCheck;
import ua.bookloom.pipeline.labels.FixedLabels;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;

/**
 * The ways a segment is decided without a draft call of its own, tried before the memory and the draft. A segment
 * with nothing to translate is kept as it is ({@link VerbatimCheck}). An auxiliary text identical to one drafted or
 * accepted earlier in the run takes that answer: one book's 58 {@code image} alt texts and 80 identical page titles
 * each cost a call before. A copied draft is still reviewed and decided on its own, and an earlier acceptance is reused
 * only after the memory's own checks pass, so every slot keeps its own record and decision.
 *
 * <p>Used from the job thread only, which is why the state is a plain map.
 */
@Slf4j
final class DraftShortcuts {

    private static final Set<SegmentKind> LABEL_KINDS =
            Set.of(SegmentKind.NAV_LABEL, SegmentKind.ALT, SegmentKind.TITLE, SegmentKind.HEADING);

    private final Map<String, String> acceptedAuxiliary = new HashMap<>();
    private final ContextSnapshot noContext;
    private final FixedLabels labels;

    /**
     * Creates the shortcuts of one run.
     *
     * @param styleSheet the non-null style sheet of the run, which a segment that saw no other context still records
     * @param labels the fixed labels of the run's language pair
     */
    DraftShortcuts(final String styleSheet, final FixedLabels labels) {
        this.labels = Objects.requireNonNull(labels, "labels");
        this.noContext = new ContextSnapshot(
                List.of(), List.of(), List.of(), null, Objects.requireNonNull(styleSheet, "styleSheet"));
    }

    /**
     * Decides a segment's draft without a call when one of the shortcuts applies, keeping it in the chunk's drafts.
     *
     * @param segment the segment about to be drafted
     * @param mask its protected mask for the chunk
     * @param gate the chunk's gate
     * @param loop the chunk's loop settings, whose review mode sets the memory's τ
     * @param drafts the chunk's drafts, which the outcome joins
     * @return the outcome that stands in for the draft, or {@code null} when the segment needs its own call
     */
    @Nullable
    DraftOutcome take(
            final Segment segment,
            final ProtectedMask mask,
            final GateFunction gate,
            final LoopSettings loop,
            final ChunkDrafts drafts) {
        final DraftOutcome.Verbatim verbatim =
                VerbatimCheck.check(segment, mask.maskedText(), mask.presentLocked(), gate);
        if (verbatim != null) {
            drafts.drafted(verbatim, noContext);
            return verbatim;
        }
        final DraftOutcome fixed = fixedLabel(segment, mask, gate, loop, drafts);
        if (fixed != null) {
            return fixed;
        }
        if (!Unit.AUXILIARY_ID.equals(segment.unit())) {
            return null;
        }
        final DraftOutcome.Drafted sibling = drafts.undecidedDraftOf(segment.masked());
        return sibling != null
                ? copied(segment, mask, gate, sibling, drafts)
                : reused(segment, mask, gate, loop, drafts);
    }

    /**
     * Remembers an accepted auxiliary target, so a later identical auxiliary text can take it.
     *
     * @param segment the decided segment
     * @param record its decided record
     */
    void decided(final Segment segment, final SegmentRecord record) {
        final String target = record.maskedMachineTarget();
        if (!Unit.AUXILIARY_ID.equals(segment.unit())
                || record.status() != SegmentStatus.ACCEPTED
                || record.path() == SegmentPath.VERBATIM
                || target == null) {
            return;
        }
        if (acceptedAuxiliary.putIfAbsent(segment.masked(), target) == null) {
            log.debug("Auxiliary target remembered segmentId={} distinct={}", segment.id(), acceptedAuxiliary.size());
        }
    }

    private @Nullable DraftOutcome copied(
            final Segment segment,
            final ProtectedMask mask,
            final GateFunction gate,
            final DraftOutcome.Drafted sibling,
            final ChunkDrafts drafts) {
        if (!(gate.restore(segment, sibling.maskedReply()) instanceof GateResult.Restored restored)) {
            log.debug("Auxiliary segmentId={} drafted on its own: the identical draft did not restore", segment.id());
            return null;
        }
        final DraftOutcome.Drafted copy = new DraftOutcome.Drafted(
                segment,
                mask.maskedText(),
                mask.presentLocked(),
                sibling.maskedReply(),
                restored.maskedForm(),
                restored.restored(),
                null,
                null,
                null,
                null,
                restored.normalised());
        log.debug(
                "Auxiliary segmentId={} takes the draft of identical segmentId={}, no call",
                segment.id(),
                sibling.segment().id());
        drafts.drafted(copy, drafts.snapshot(sibling.segment().id()));
        return copy;
    }

    // A navigation label, heading, page title or image description that is only "Cover" or "Contents" is the same word
    // in
    // every book: the pair's table answers it, through the same checks a memory reuse passes, and no call is made.
    private @Nullable DraftOutcome fixedLabel(
            final Segment segment,
            final ProtectedMask mask,
            final GateFunction gate,
            final LoopSettings loop,
            final ChunkDrafts drafts) {
        if (!LABEL_KINDS.contains(segment.kind())
                || !Tokens.inOrder(segment.masked()).isEmpty()) {
            return null;
        }
        final String label = labels.resolve(segment.masked()).orElse(null);
        if (label == null) {
            return null;
        }
        log.debug("Segment segmentId={} kind={} is a fixed label, no call", segment.id(), segment.kind());
        return reuse(label, segment, mask, gate, loop, drafts);
    }

    private @Nullable DraftOutcome reused(
            final Segment segment,
            final ProtectedMask mask,
            final GateFunction gate,
            final LoopSettings loop,
            final ChunkDrafts drafts) {
        final String stored = acceptedAuxiliary.get(segment.masked());
        if (stored == null) {
            log.debug("Auxiliary segmentId={} drafted: no identical text decided yet", segment.id());
            return null;
        }
        return reuse(stored, segment, mask, gate, loop, drafts);
    }

    private @Nullable DraftOutcome reuse(
            final String stored,
            final Segment segment,
            final ProtectedMask mask,
            final GateFunction gate,
            final LoopSettings loop,
            final ChunkDrafts drafts) {
        final Result<DraftOutcome.Reused> checked = ProtectedSpans.checkRestored(stored, mask)
                .flatMap(remasked ->
                        ReuseCheck.check(segment, mask.maskedText(), mask.presentLocked(), remasked, gate, loop));
        final DraftOutcome.Reused reused = checked.data();
        if (reused == null) {
            log.debug(
                    "Auxiliary segmentId={} drafted: the identical target was refused code={}",
                    segment.id(),
                    Objects.requireNonNull(checked.error(), "error").code());
            return null;
        }
        log.debug("Auxiliary segmentId={} takes the accepted target of an identical text, no call", segment.id());
        drafts.drafted(reused, noContext);
        return reused;
    }
}
