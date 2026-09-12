package ua.bookloom.document.golden;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * One flat, human-scannable TSV row per book for the corpus verification's summary report (design.md D6, task
 * 11.1). The JSON line ({@link CorpusJson}) alongside it carries every field this loses to flattening.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusTsv {

    static String rowOf(CorpusBookOutcome outcome) {
        return String.join(
                "\t",
                cell(outcome.fileName()),
                openStatusOf(outcome.open()),
                unitCountOf(outcome.open()),
                segmentCountOf(outcome.open()),
                identityStatusOf(outcome.identity()),
                identityCanonicalOf(outcome.identity()),
                fixedPointStatusOf(outcome.fixedPoint()),
                fixedPointCanonicalOf(outcome.fixedPoint()),
                mutationStatusOf(outcome.mutation()),
                mutationCountsMatchOf(outcome.mutation()),
                mutationTuplesMatchOf(outcome.mutation()),
                mutationMarkerCleanOf(outcome.mutation()),
                idempotenceStatusOf(outcome.idempotence()),
                idempotenceTuplesMatchOf(outcome.idempotence()),
                idempotenceSourceMatchOf(outcome.idempotence()),
                maskStatusOf(outcome.mask()),
                maskOkOf(outcome.mask()),
                maskTotalPlaceholdersOf(outcome.mask()),
                maskMaxPlaceholdersOf(outcome.mask()),
                maskSegmentsWithPlaceholdersOf(outcome.mask()),
                maskSkippedCodeOnlyBlocksOf(outcome.mask()),
                Long.toString(outcome.resource().wallClockMs()),
                Long.toString(outcome.resource().sourceFileSizeBytes()),
                textCoverageOf(outcome.open()));
    }

    private static String textCoverageOf(CorpusOpenOutcome open) {
        return open instanceof CorpusOpenOutcome.Opened opened ? String.format("%.4f", opened.textCoverage()) : "-";
    }

    private static String openStatusOf(CorpusOpenOutcome open) {
        return switch (open) {
            case CorpusOpenOutcome.Failed failed -> "failed:" + failed.errorCode();
            case CorpusOpenOutcome.Opened ignored -> "opened";
        };
    }

    private static String unitCountOf(CorpusOpenOutcome open) {
        return open instanceof CorpusOpenOutcome.Opened opened ? Integer.toString(opened.unitCount()) : "-";
    }

    private static String segmentCountOf(CorpusOpenOutcome open) {
        return open instanceof CorpusOpenOutcome.Opened opened ? Integer.toString(opened.segmentCount()) : "-";
    }

    private static String identityStatusOf(CorpusIdentityOutcome identity) {
        return switch (identity) {
            case CorpusIdentityOutcome.NotAttempted ignored -> "notAttempted";
            case CorpusIdentityOutcome.OpenFailed openFailed -> "openFailed:" + openFailed.errorCode();
            case CorpusIdentityOutcome.WriteFailed writeFailed -> "writeFailed:" + writeFailed.errorCode();
            case CorpusIdentityOutcome.Written ignored -> "written";
        };
    }

    private static String identityCanonicalOf(CorpusIdentityOutcome identity) {
        return identity instanceof CorpusIdentityOutcome.Written written
                ? Boolean.toString(written.canonicalEqual())
                : "-";
    }

    private static String fixedPointStatusOf(CorpusFixedPointOutcome fixedPoint) {
        return switch (fixedPoint) {
            case CorpusFixedPointOutcome.NotAttempted ignored -> "notAttempted";
            case CorpusFixedPointOutcome.ReopenFailed reopenFailed -> "reopenFailed:" + reopenFailed.errorCode();
            case CorpusFixedPointOutcome.WriteFailed writeFailed -> "writeFailed:" + writeFailed.errorCode();
            case CorpusFixedPointOutcome.Written ignored -> "written";
        };
    }

    private static String fixedPointCanonicalOf(CorpusFixedPointOutcome fixedPoint) {
        return fixedPoint instanceof CorpusFixedPointOutcome.Written written
                ? Boolean.toString(written.canonicalEqual())
                : "-";
    }

    private static String mutationStatusOf(CorpusMutationOutcome mutation) {
        return switch (mutation) {
            case CorpusMutationOutcome.NotAttempted ignored -> "notAttempted";
            case CorpusMutationOutcome.OpenFailed openFailed -> "openFailed:" + openFailed.errorCode();
            case CorpusMutationOutcome.WriteFailed writeFailed -> mutationWriteFailedCellOf(writeFailed);
            case CorpusMutationOutcome.ReopenFailed reopenFailed -> "reopenFailed:" + reopenFailed.errorCode();
            case CorpusMutationOutcome.Completed ignored -> "completed";
        };
    }

    private static String mutationWriteFailedCellOf(CorpusMutationOutcome.WriteFailed writeFailed) {
        final String prefix = writeFailed.validationRefusal() ? "validationRefusal:" : "writeFailed:";
        return prefix + writeFailed.errorCode();
    }

    private static String mutationCountsMatchOf(CorpusMutationOutcome mutation) {
        return mutation instanceof CorpusMutationOutcome.Completed completed
                ? Boolean.toString(completed.countsMatchOpen())
                : "-";
    }

    private static String mutationTuplesMatchOf(CorpusMutationOutcome mutation) {
        return mutation instanceof CorpusMutationOutcome.Completed completed
                ? Boolean.toString(completed.tuplesMatchOpen())
                : "-";
    }

    private static String mutationMarkerCleanOf(CorpusMutationOutcome mutation) {
        return mutation instanceof CorpusMutationOutcome.Completed completed
                ? Boolean.toString(completed.markerStripClean())
                : "-";
    }

    private static String idempotenceStatusOf(CorpusIdempotenceOutcome idempotence) {
        return switch (idempotence) {
            case CorpusIdempotenceOutcome.NotAttempted ignored -> "notAttempted";
            case CorpusIdempotenceOutcome.ReopenFailed reopenFailed -> "reopenFailed:" + reopenFailed.errorCode();
            case CorpusIdempotenceOutcome.Completed ignored -> "completed";
        };
    }

    private static String idempotenceTuplesMatchOf(CorpusIdempotenceOutcome idempotence) {
        return idempotence instanceof CorpusIdempotenceOutcome.Completed completed
                ? Boolean.toString(completed.tuplesMatchOpen())
                : "-";
    }

    private static String idempotenceSourceMatchOf(CorpusIdempotenceOutcome idempotence) {
        return idempotence instanceof CorpusIdempotenceOutcome.Completed completed
                ? Boolean.toString(completed.sourceTextMatchOpen())
                : "-";
    }

    private static String maskStatusOf(CorpusMaskOutcome mask) {
        return switch (mask) {
            case CorpusMaskOutcome.NotAttempted ignored -> "notAttempted";
            case CorpusMaskOutcome.Completed ignored -> "completed";
        };
    }

    private static String maskOkOf(CorpusMaskOutcome mask) {
        return mask instanceof CorpusMaskOutcome.Completed completed ? Boolean.toString(completed.ok()) : "-";
    }

    private static String maskTotalPlaceholdersOf(CorpusMaskOutcome mask) {
        return mask instanceof CorpusMaskOutcome.Completed completed
                ? Integer.toString(completed.totalPlaceholders())
                : "-";
    }

    private static String maskMaxPlaceholdersOf(CorpusMaskOutcome mask) {
        return mask instanceof CorpusMaskOutcome.Completed completed
                ? Integer.toString(completed.maxPlaceholdersInOneSegment())
                : "-";
    }

    private static String maskSegmentsWithPlaceholdersOf(CorpusMaskOutcome mask) {
        return mask instanceof CorpusMaskOutcome.Completed completed
                ? Integer.toString(completed.segmentsWithPlaceholders())
                : "-";
    }

    /** {@code "notMeasured"} distinguishes "this run does not compute the count" from a genuine {@code 0} — see
     * {@link CorpusMaskProbe}'s class Javadoc for why the harness does not re-derive it. */
    private static String maskSkippedCodeOnlyBlocksOf(CorpusMaskOutcome mask) {
        if (!(mask instanceof CorpusMaskOutcome.Completed completed)) {
            return "-";
        }
        return completed.skippedCodeOnlyBlocks() == null
                ? "notMeasured"
                : completed.skippedCodeOnlyBlocks().toString();
    }

    private static String cell(String value) {
        return value.replace('\t', ' ').replace('\n', ' ');
    }
}
