package ua.bookloom.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.CallSeconds;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Words the per-kind call totals the one way every summary line prints them — a run's final line and the
 * {@code prep summary} line the names pass, the brief suggestion and the export end with — so the cost of each part of
 * the work reads the same in a shared log. A figure no call reported prints as {@value #NOT_REPORTED}, never 0.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PrepSummary {

    /** What a figure no call reported prints as. */
    static final String NOT_REPORTED = "–";

    private static final double PERCENT = 100.0;

    /**
     * Writes the one INFO line a preparation step ends with, with the seconds per call of each kind it measured.
     *
     * @param stage the step's short name, {@code names}, {@code brief} or {@code export}
     * @param projectId the project the step worked on
     * @param records the calls the step made; none lists no kind
     */
    public static void log(final String stage, final String projectId, final List<CallRecord> records) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(records, "records");
        log.info(
                "prep summary byKind[{}]{} stage={} project={}",
                byKind(CallKindTotals.of(records).totals()),
                secondsPerCall(records),
                stage,
                projectId);
    }

    /**
     * The seconds per answered call of each kind, as a run or a names pass stores them on the model.
     *
     * @param records the calls of the run or step; never null
     * @return {@code " secondsPerCall[KIND=s …]"} in kind order, or empty when no kind but {@code DRAFT} answered
     */
    public static String secondsPerCall(final List<CallRecord> records) {
        final Map<CallKind, Double> seconds = CallSeconds.of(records);
        if (seconds.isEmpty()) {
            return "";
        }
        return seconds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.format(Locale.ROOT, "%.1f", entry.getValue()))
                .collect(Collectors.joining(" ", " secondsPerCall[", "]"));
    }

    /**
     * Writes the one INFO line a preparation step ends with.
     *
     * @param stage the step's short name, {@code names}, {@code brief} or {@code export}
     * @param projectId the project the step worked on
     * @param totals the calls the step made, by kind; none lists no kind
     */
    public static void log(final String stage, final String projectId, final CallKindTotals totals) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(totals, "totals");
        log.info("prep summary byKind[{}] stage={} project={}", byKind(totals.totals()), stage, projectId);
    }

    /**
     * The totals of each kind, space-separated, as {@code KIND=attempts/failed in:… out:… promptEvalMs:…
     * generationMs:… cached:… ttftMs:… prefill:…}.
     *
     * @param totals the totals by kind
     * @return never null; empty if no kind had a call
     */
    public static String byKind(final Map<CallKind, CallKindTotals.Totals> totals) {
        Objects.requireNonNull(totals, "totals");
        return totals.entrySet().stream()
                .map(entry -> kindLine(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(" "));
    }

    private static String kindLine(final CallKind kind, final CallKindTotals.Totals totals) {
        return String.format(
                Locale.ROOT,
                "%s=%d/%d in:%s out:%s promptEvalMs:%s generationMs:%s cached:%s ttftMs:%s prefill:%s",
                kind,
                totals.attempts(),
                totals.failed(),
                totals.promptTotal().map(String::valueOf).orElse(NOT_REPORTED),
                totals.completionTotal().map(String::valueOf).orElse(NOT_REPORTED),
                totals.promptEvalTotal().map(PrepSummary::millis).orElse(NOT_REPORTED),
                totals.generationTotal().map(PrepSummary::millis).orElse(NOT_REPORTED),
                totals.cachedTotal().map(String::valueOf).orElse(NOT_REPORTED),
                totals.meanFirstToken().map(PrepSummary::millis).orElse(NOT_REPORTED),
                totals.prefillShare()
                        .map(share -> String.format(Locale.ROOT, "%.1f%%", share * PERCENT))
                        .orElse(NOT_REPORTED));
    }

    private static String millis(final Duration time) {
        return String.valueOf(time.toMillis());
    }
}
