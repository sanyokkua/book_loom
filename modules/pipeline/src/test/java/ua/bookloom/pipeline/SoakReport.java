package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Writes a soak run's numbers as one INFO line, so a run's evidence is in the test log, not only in its verdict. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SoakReport {

    static void print(final String name, final SoakRun.Outcome outcome) {
        final String line = String.format(
                java.util.Locale.ROOT,
                "soak %s: %s segments=%d accepted=%d flagged=%d counts=%s exported=%d calls=%d elapsed=%s virtual=%s"
                        + " heap=%s bytesPerSegment=%d threads=%d->%d %s injected=%s errors=%d",
                name,
                outcome.report().end(),
                outcome.documentSegments(),
                outcome.report().accepted(),
                outcome.report().flagged(),
                outcome.counts(),
                outcome.exportedSegments(),
                outcome.calls(),
                outcome.elapsed(),
                outcome.virtual(),
                outcome.heap(),
                outcome.heap().bytesPerSegment(outcome.documentSegments()),
                outcome.threadsBefore(),
                outcome.threadsAfter(),
                outcome.events(),
                outcome.injected(),
                outcome.errors().size());
        log.info("{}", line);
    }
}
