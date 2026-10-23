package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.QualityDial;

/** Decides whether a book has a measured time before Start or only the relative cost of the quality choices. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class StartEstimates {

    /** How much longer Balanced takes than Fast. */
    static final double BALANCED_FACTOR = 1.5;

    /** How much longer Max takes than Fast. */
    static final double MAX_FACTOR = 3.0;

    private static final double MILLIS_PER_SECOND = 1000.0;

    /**
     * The estimate for a book.
     *
     * @param measuredSecondsPerSegment where the stored speed is read, in seconds per segment of a Fast draft: empty
     *     when no inference test and no earlier translation measured one
     * @param segments the segments the book will translate
     * @param dial the quality chosen
     * @return a measured time for the book when a speed is stored, else the relative costs
     */
    public static StartEstimate of(
            final Supplier<Optional<ModelSpeed>> measuredSecondsPerSegment,
            final int segments,
            final QualityDial dial) {
        Objects.requireNonNull(measuredSecondsPerSegment, "measuredSecondsPerSegment");
        Objects.requireNonNull(dial, "dial");
        final Optional<ModelSpeed> speed = measuredSecondsPerSegment.get();
        if (speed.isEmpty() || speed.get().seconds() <= 0 || segments <= 0) {
            log.debug("start estimate: no measured speed or no segments, showing the relative cost");
            return new StartEstimate.Relative();
        }
        final double factor =
                switch (dial) {
                    case FAST -> 1.0;
                    case BALANCED -> BALANCED_FACTOR;
                    case MAX -> MAX_FACTOR;
                };
        final Duration book =
                Duration.ofMillis(Math.round(segments * speed.get().seconds() * factor * MILLIS_PER_SECOND));
        log.debug(
                "start estimate: {} segments at {} s each, {} -> {}",
                segments,
                speed.get().seconds(),
                dial,
                book);
        return new StartEstimate.Measured(book, speed.get().from());
    }
}
