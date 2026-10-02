package ua.bookloom.pipeline;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The fault plan of the fixture-book runs: dense faults and a short outage early on, so a small book meets them. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FixtureSoak {

    private static final long SEED = 11L;
    private static final int OUTAGE_AT_CALL = 40;
    private static final int SCALE = 3;

    static FaultPlan plan() {
        return new FaultPlan(SEED, OUTAGE_AT_CALL, Duration.ofMinutes(21), SCALE);
    }
}
