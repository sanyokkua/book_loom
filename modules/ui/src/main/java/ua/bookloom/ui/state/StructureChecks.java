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
     * @param runSegments how many segments a run translates — the book text and the other texts the brief also
     *     translates (titles, image descriptions, contents, book details), the figure the Translating screen starts
     *     its remaining count from; zero when the counts could not be read
     */
    record Finished(@Nullable RoundTripReport report, int oversizedSegments, int runSegments)
            implements StructureChecks {

        /** Rejects a negative count. */
        public Finished {
            if (oversizedSegments < 0 || runSegments < 0) {
                throw new IllegalArgumentException("counts must be >= 0");
            }
        }

        /**
         * The checks' answer without the run's segment count.
         *
         * @param report what the round-trip check found, or {@code null} when it could not be run
         * @param oversizedSegments how many segments are larger than one chunk
         */
        public Finished(final @Nullable RoundTripReport report, final int oversizedSegments) {
            this(report, oversizedSegments, 0);
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
