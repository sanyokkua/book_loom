package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentView;

/**
 * Which segment the review panel shows after an action: a decision (Accept, Save edit) moves on to the next listed
 * segment after the decided one, in the order the list showed, so the panel never keeps showing a segment that has
 * left the list; Skip goes where the desk moved on to; the other actions (Revert, Retry, a proposal) stay on the
 * segment, which still needs the person. Kept apart from {@link ReviewViewModel} so that class stays a readable size.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewNext {

    private static final Set<String> DECISIONS = Set.of("accept", "saveEdit");

    /**
     * Chooses the segment to show after an action.
     *
     * @param action the action's name, as the view model logs it
     * @param decided the id of the segment the action was on
     * @param deskNext the id of the segment the desk's answer names (for Skip, the one it moved on to)
     * @param shownOrder the ids the list showed before the action, in order
     * @param listed the list as it stands after the action
     * @return the id to show, or {@code null} when nothing is left to show
     */
    static @Nullable String after(
            final String action,
            final String decided,
            final String deskNext,
            final List<String> shownOrder,
            final List<SegmentView> listed) {
        Objects.requireNonNull(action, "action");
        if ("skip".equals(action)) {
            log.debug("after skip the desk moved from {} to {}", decided, deskNext);
            return deskNext;
        }
        if (!DECISIONS.contains(action)) {
            log.debug("after {} the panel stays on {}", action, decided);
            return decided;
        }
        final Set<String> remaining = listed.stream()
                .map(SegmentView::segmentId)
                .filter(id -> !id.equals(decided))
                .collect(Collectors.toSet());
        final int at = shownOrder.indexOf(decided);
        final String next = shownOrder.stream()
                .skip(at < 0 ? 0 : at + 1L)
                .filter(remaining::contains)
                .findFirst()
                .orElseGet(() -> listed.stream()
                        .map(SegmentView::segmentId)
                        .filter(remaining::contains)
                        .findFirst()
                        .orElse(null));
        log.debug("after {} of {} the panel moves to {} ({} still listed)", action, decided, next, remaining.size());
        return next;
    }
}
