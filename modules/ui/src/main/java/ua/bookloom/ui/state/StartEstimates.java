package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * The one rule of the time estimate before Start (design D3 "Estimates"): the planned calls of each kind times the
 * seconds a run measured for each on this model when it measured every kind the plan makes; else a range from this
 * model's seconds per drafted segment, scaled by the dial; else nothing. {@code DRAFT} is seconds per drafted segment,
 * every other kind seconds per call.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class StartEstimates {

    /** How much longer Balanced takes than Fast when only the draft speed is known. */
    static final double BALANCED_FACTOR = 1.5;

    /** How much longer Max takes than Fast when only the draft speed is known. */
    static final double MAX_FACTOR = 3.0;

    /** The range runs from this share of the scaled time... */
    static final double LOW_SHARE = 0.8;

    /** ...to this share. */
    static final double HIGH_SHARE = 1.2;

    /** From this end of the range on it is told in whole hours, below in five minutes. */
    static final Duration HOURS_FROM = Duration.ofHours(2);

    private static final long STEP_MINUTES = 5;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3600;
    private static final double MILLIS_PER_SECOND = 1000.0;

    /**
     * The estimate for a book.
     *
     * @param stored the seconds of each call kind measured on the chosen model; never null, empty when never measured
     * @param segments the segments the book will translate
     * @param chunks the chunks those segments are packed into
     * @param dial the quality chosen; never null
     * @param reviewPasses how many times the reviewer reads each chunk at that quality; zero for none
     * @return measured when a run measured every planned kind, a range from the draft speed when there is one, else
     *     {@link StartEstimate.None}
     */
    public static StartEstimate of(
            final Map<CallKind, MeasuredSpeed> stored,
            final int segments,
            final int chunks,
            final QualityDial dial,
            final int reviewPasses) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(dial, "dial");
        final MeasuredSpeed draft = stored.get(CallKind.DRAFT);
        if (draft == null || segments <= 0) {
            log.debug("start estimate: no draft speed stored or no segments, no time shown");
            return new StartEstimate.None();
        }
        final Map<CallKind, Integer> planned = planned(segments, chunks, reviewPasses);
        if (planned.keySet().stream().allMatch(kind -> isRunMeasured(stored.get(kind)))) {
            final double seconds = planned.entrySet().stream()
                    .mapToDouble(entry -> entry.getValue()
                            * Objects.requireNonNull(stored.get(entry.getKey()), "stored")
                                    .seconds())
                    .sum();
            log.debug("start estimate: measured, {} -> {} s on {}", planned, seconds, dial);
            return new StartEstimate.Measured(Duration.ofMillis(Math.round(seconds * MILLIS_PER_SECOND)));
        }
        final double scaled = segments * draft.seconds() * factorOf(dial);
        final StartEstimate.Ranged range = rangeOf(scaled, draft.from());
        log.debug(
                "start estimate: range from the draft speed {} s ({}), {} segments on {}: {} s -> {}",
                draft.seconds(),
                draft.from(),
                segments,
                dial,
                scaled,
                range);
        return range;
    }

    /**
     * A time rounded to the nearest whole minute, as the measured line tells it.
     *
     * @param time the non-null time
     * @return the time in whole minutes
     */
    static Duration wholeMinutes(final Duration time) {
        return Duration.ofMinutes(Math.round(time.toSeconds() / (double) SECONDS_PER_MINUTE));
    }

    private static Map<CallKind, Integer> planned(final int segments, final int chunks, final int reviewPasses) {
        return reviewPasses > 0 && chunks > 0
                ? Map.of(CallKind.DRAFT, segments, CallKind.REVIEW, chunks * reviewPasses)
                : Map.of(CallKind.DRAFT, segments);
    }

    private static boolean isRunMeasured(@Nullable final MeasuredSpeed speed) {
        return speed != null && speed.from() == MeasuredFrom.RUN;
    }

    private static double factorOf(final QualityDial dial) {
        return switch (dial) {
            case FAST -> 1.0;
            case BALANCED -> BALANCED_FACTOR;
            case MAX -> MAX_FACTOR;
        };
    }

    // Whole hours from two hours on, else five minutes; the start rounded down (never below one step), the end up.
    private static StartEstimate.Ranged rangeOf(final double seconds, final MeasuredFrom from) {
        final long low = Math.round(seconds * LOW_SHARE);
        final long high = Math.round(seconds * HIGH_SHARE);
        final long step = high >= HOURS_FROM.toSeconds() ? SECONDS_PER_HOUR : STEP_MINUTES * SECONDS_PER_MINUTE;
        final long first = Math.max(step == SECONDS_PER_HOUR ? step : SECONDS_PER_MINUTE, low / step * step);
        final long last = Math.max(first, Math.ceilDiv(high, step) * step);
        return new StartEstimate.Ranged(Duration.ofSeconds(first), Duration.ofSeconds(last), from);
    }
}
