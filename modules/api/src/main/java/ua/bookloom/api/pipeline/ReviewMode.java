package ua.bookloom.api.pipeline;

import java.util.Objects;
import java.util.Set;

/**
 * How much of a run's outcome a person must confirm before it is treated as decided, resolved once at launch
 * ({@code specs/translation-pipeline/spec.md} "Resolve the review mode once at launch").
 */
// Each constant's Set.of(...) is genuinely immutable at runtime, copied again defensively in the constructor;
// Error Prone's ImmutableEnumChecker only checks the declared field type.
@SuppressWarnings("ImmutableEnumChecker")
public enum ReviewMode {

    /** Nothing pauses for review; every segment is decided automatically. */
    UNATTENDED(Set.of()),

    /** Pauses when a segment is flagged or an otherwise terminal error occurs. */
    ASSISTED(Set.of(PausePoint.ON_FLAGGED, PausePoint.ON_ERROR)),

    /** Pauses after every segment, when one is flagged, and on error. */
    MANUAL(Set.of(PausePoint.AFTER_SEGMENT, PausePoint.ON_FLAGGED, PausePoint.ON_ERROR));

    private final Set<PausePoint> pausePoints;

    ReviewMode(final Set<PausePoint> pausePoints) {
        this.pausePoints = Set.copyOf(Objects.requireNonNull(pausePoints, "pausePoints"));
    }

    /**
     * Returns the pause points this mode enables.
     *
     * @return an unmodifiable, possibly empty set
     */
    public Set<PausePoint> pausePoints() {
        return pausePoints;
    }
}
