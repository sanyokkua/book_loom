package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.RoundTripReport;

/**
 * Where the structure screen's background checks stand: still running, or finished with what they found.
 */
public sealed interface StructureChecks {

    /** The checks have been asked for and have not answered. */
    record Running() implements StructureChecks {}

    /**
     * The checks have answered.
     *
     * @param report what the round-trip check found, or {@code null} when it could not be run
     * @param oversizedSegments how many segments are larger than one chunk; zero when the plan was not available
     */
    record Finished(@Nullable RoundTripReport report, int oversizedSegments) implements StructureChecks {

        /** Rejects a negative count. */
        public Finished {
            if (oversizedSegments < 0) {
                throw new IllegalArgumentException("oversizedSegments must be >= 0");
            }
        }

        /**
         * Whether the round-trip check ran at all.
         *
         * @return {@code true} if a report is present
         */
        public boolean hasReport() {
            return report != null;
        }

        /**
         * The report.
         *
         * @return the report; callers check {@link #hasReport()} first
         */
        public RoundTripReport requiredReport() {
            return Objects.requireNonNull(report, "report");
        }
    }
}
