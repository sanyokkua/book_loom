package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * Keeps a segment with nothing to translate as it is, before any call: its shown text is its own reply, put back
 * through the chunk's gate so a locked name comes back as its rendering and the markup is checked like any target.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VerbatimCheck {

    /**
     * Checks one segment.
     *
     * @param segment the segment about to be drafted; never null
     * @param shownText the text a draft would be shown, protected spans behind tokens; never null
     * @param lockedRenderings the locked glossary terms present in the segment; never null
     * @param gate the chunk's gate: protected spans back, then the document's own markup; never null
     * @return the verbatim outcome, or {@code null} when the segment holds text to translate or, unexpectedly, its own
     *     text does not pass the gate — then it is drafted as usual and the draft step reports the gate
     */
    public static DraftOutcome.@Nullable Verbatim check(
            final Segment segment,
            final String shownText,
            final List<LockedRendering> lockedRenderings,
            final GateFunction gate) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(shownText, "shownText");
        Objects.requireNonNull(lockedRenderings, "lockedRenderings");
        Objects.requireNonNull(gate, "gate");
        final VerbatimRule rule = VerbatimRule.match(shownText, !lockedRenderings.isEmpty());
        if (rule == null) {
            log.debug("Verbatim rule segmentId={} matched=none", segment.id());
            return null;
        }
        return switch (gate.restore(segment, shownText)) {
            case GateResult.Restored restored -> {
                log.debug("Verbatim rule segmentId={} matched={}: kept as it is, no model call", segment.id(), rule);
                yield new DraftOutcome.Verbatim(
                        segment, shownText, lockedRenderings, restored.maskedForm(), restored.restored(), rule);
            }
            case GateResult.GateFailed failed ->
                refused(segment, rule, failed.finding().kind());
            case GateResult.StepError error ->
                refused(segment, rule, error.error().code().name());
        };
    }

    private static DraftOutcome.@Nullable Verbatim refused(
            final Segment segment, final VerbatimRule rule, final String reason) {
        log.warn(
                "Verbatim rule segmentId={} matched={} but its own text failed the gate ({}); drafting it",
                segment.id(),
                rule,
                reason);
        return null;
    }
}
