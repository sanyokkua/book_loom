package ua.bookloom.app.cli;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.NamePolicy;

/**
 * How the command runs a book beyond which book and which model: the brief's quality dial and name policy, the names
 * review before translating, how long an outage is waited through, whether a run that did not complete still writes
 * what it has, and where its JSON report goes.
 *
 * @param quality the quality dial, or {@code null} to keep the brief's
 * @param names the name policy, or {@code null} to keep the brief's
 * @param reviewNames whether the names are scanned, reviewed by the model and every suggested target accepted before
 *     translating
 * @param maxOutage the longest provider outage the run waits through by itself; positive
 * @param partialExport whether a run that ended failed, cancelled or interrupted still exports what it translated
 * @param report the file the JSON run report is written to, or {@code null} for none
 */
record RunOptions(
        @Nullable QualityDial quality,
        @Nullable NamePolicy names,
        boolean reviewNames,
        Duration maxOutage,
        boolean partialExport,
        @Nullable Path report) {

    /** The options of a command that names none of them. */
    static final RunOptions DEFAULTS = new RunOptions(null, null, false, TranslationJob.DEFAULT_MAX_OUTAGE, true, null);

    /** Rejects an outage limit that is absent or not positive. */
    RunOptions {
        Objects.requireNonNull(maxOutage, "maxOutage");
        if (maxOutage.isNegative() || maxOutage.isZero()) {
            throw new IllegalArgumentException("maxOutage must be positive: " + maxOutage);
        }
    }
}
