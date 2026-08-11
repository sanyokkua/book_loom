package ua.bookloom.document.golden;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Hand-rolled JSON for the corpus verification's report — {@code :document} carries no JSON library, and the
 * report only ever needs the handful of scalar shapes below (design.md D6, task 11.1).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusJson {

    static String jsonOf(CorpusBookOutcome outcome) {
        return new JsonWriter()
                .field("file", outcome.fileName())
                .rawField("open", jsonOf(outcome.open()))
                .rawField("identity", jsonOf(outcome.identity()))
                .rawField("fixedPoint", jsonOf(outcome.fixedPoint()))
                .rawField("mutation", jsonOf(outcome.mutation()))
                .rawField("idempotence", jsonOf(outcome.idempotence()))
                .rawField("resource", jsonOf(outcome.resource()))
                .build();
    }

    private static String jsonOf(CorpusOpenOutcome open) {
        return switch (open) {
            case CorpusOpenOutcome.Failed failed ->
                new JsonWriter()
                        .field("status", "failed")
                        .field("errorCode", failed.errorCode())
                        .build();
            case CorpusOpenOutcome.Opened opened -> jsonOfOpened(opened);
        };
    }

    private static String jsonOfOpened(CorpusOpenOutcome.Opened opened) {
        return new JsonWriter()
                .field("status", "opened")
                .field("format", opened.format())
                .field("charset", opened.charset())
                .field("hasBom", opened.hasBom())
                .field("declaredLang", opened.declaredLang())
                .field("unitCount", opened.unitCount())
                .field("segmentCount", opened.segmentCount())
                .rawField("kindHistogram", jsonOf(opened.kindHistogram()))
                .field("minSegmentLength", opened.minSegmentLength())
                .field("medianSegmentLength", opened.medianSegmentLength())
                .field("p95SegmentLength", opened.p95SegmentLength())
                .field("maxSegmentLength", opened.maxSegmentLength())
                .field("emptyOrDuplicateIdSegmentCount", opened.emptyOrDuplicateIdSegmentCount())
                .field("elapsedMs", opened.elapsedMs())
                .build();
    }

    private static String jsonOf(CorpusIdentityOutcome identity) {
        return switch (identity) {
            case CorpusIdentityOutcome.NotAttempted ignored ->
                new JsonWriter().field("status", "notAttempted").build();
            case CorpusIdentityOutcome.OpenFailed openFailed ->
                new JsonWriter()
                        .field("status", "openFailed")
                        .field("errorCode", openFailed.errorCode())
                        .build();
            case CorpusIdentityOutcome.WriteFailed writeFailed ->
                new JsonWriter()
                        .field("status", "writeFailed")
                        .field("errorCode", writeFailed.errorCode())
                        .build();
            case CorpusIdentityOutcome.Written written ->
                new JsonWriter()
                        .field("status", "written")
                        .field("rawBytesEqual", written.rawBytesEqual())
                        .field("canonicalEqual", written.canonicalEqual())
                        .field("canonicalFailureMessage", written.canonicalFailureMessage())
                        .build();
        };
    }

    private static String jsonOf(CorpusFixedPointOutcome fixedPoint) {
        return switch (fixedPoint) {
            case CorpusFixedPointOutcome.NotAttempted ignored ->
                new JsonWriter().field("status", "notAttempted").build();
            case CorpusFixedPointOutcome.ReopenFailed reopenFailed ->
                new JsonWriter()
                        .field("status", "reopenFailed")
                        .field("errorCode", reopenFailed.errorCode())
                        .build();
            case CorpusFixedPointOutcome.WriteFailed writeFailed ->
                new JsonWriter()
                        .field("status", "writeFailed")
                        .field("errorCode", writeFailed.errorCode())
                        .build();
            case CorpusFixedPointOutcome.Written written ->
                new JsonWriter()
                        .field("status", "written")
                        .field("rawBytesEqual", written.rawBytesEqual())
                        .field("canonicalEqual", written.canonicalEqual())
                        .build();
        };
    }

    private static String jsonOf(CorpusMutationOutcome mutation) {
        return switch (mutation) {
            case CorpusMutationOutcome.NotAttempted ignored ->
                new JsonWriter().field("status", "notAttempted").build();
            case CorpusMutationOutcome.OpenFailed openFailed ->
                new JsonWriter()
                        .field("status", "openFailed")
                        .field("errorCode", openFailed.errorCode())
                        .build();
            case CorpusMutationOutcome.WriteFailed writeFailed ->
                new JsonWriter()
                        .field("status", "writeFailed")
                        .field("errorCode", writeFailed.errorCode())
                        .field("validationRefusal", writeFailed.validationRefusal())
                        .build();
            case CorpusMutationOutcome.ReopenFailed reopenFailed ->
                new JsonWriter()
                        .field("status", "reopenFailed")
                        .field("errorCode", reopenFailed.errorCode())
                        .build();
            case CorpusMutationOutcome.Completed completed -> jsonOfCompleted(completed);
        };
    }

    private static String jsonOfCompleted(CorpusMutationOutcome.Completed completed) {
        return new JsonWriter()
                .field("status", "completed")
                .field("unitCount", completed.unitCount())
                .field("segmentCount", completed.segmentCount())
                .field("countsMatchOpen", completed.countsMatchOpen())
                .field("tuplesMatchOpen", completed.tuplesMatchOpen())
                .field("markerStripClean", completed.markerStripClean())
                .field("markerMismatchCount", completed.markerMismatchCount())
                .field("markerMissingCount", completed.markerMissingCount())
                .build();
    }

    private static String jsonOf(CorpusIdempotenceOutcome idempotence) {
        return switch (idempotence) {
            case CorpusIdempotenceOutcome.NotAttempted ignored ->
                new JsonWriter().field("status", "notAttempted").build();
            case CorpusIdempotenceOutcome.ReopenFailed reopenFailed ->
                new JsonWriter()
                        .field("status", "reopenFailed")
                        .field("errorCode", reopenFailed.errorCode())
                        .build();
            case CorpusIdempotenceOutcome.Completed completed ->
                new JsonWriter()
                        .field("status", "completed")
                        .field("tuplesMatchOpen", completed.tuplesMatchOpen())
                        .field("sourceTextMatchOpen", completed.sourceTextMatchOpen())
                        .build();
        };
    }

    private static String jsonOf(CorpusResourceOutcome resource) {
        return new JsonWriter()
                .field("wallClockMs", resource.wallClockMs())
                .field("sourceFileSizeBytes", resource.sourceFileSizeBytes())
                .build();
    }

    private static String jsonOf(Map<String, Integer> histogram) {
        final JsonWriter writer = new JsonWriter();
        for (final Map.Entry<String, Integer> entry : histogram.entrySet()) {
            writer.field(entry.getKey(), entry.getValue());
        }
        return writer.build();
    }

    /** A minimal, hand-rolled JSON object builder over the six scalar shapes this report ever needs. */
    private static final class JsonWriter {
        private final StringBuilder text = new StringBuilder("{");
        private boolean firstField = true;

        JsonWriter field(String key, @Nullable String value) {
            appendKey(key);
            text.append(value == null ? "null" : "\"" + escape(value) + "\"");
            return this;
        }

        JsonWriter field(String key, int value) {
            appendKey(key);
            text.append(value);
            return this;
        }

        JsonWriter field(String key, long value) {
            appendKey(key);
            text.append(value);
            return this;
        }

        JsonWriter field(String key, boolean value) {
            appendKey(key);
            text.append(value);
            return this;
        }

        JsonWriter field(String key, @Nullable Boolean value) {
            appendKey(key);
            text.append(value == null ? "null" : value.toString());
            return this;
        }

        JsonWriter rawField(String key, String rawJson) {
            appendKey(key);
            text.append(rawJson);
            return this;
        }

        String build() {
            return text.append('}').toString();
        }

        private void appendKey(String key) {
            if (!firstField) {
                text.append(',');
            }
            firstField = false;
            text.append('"').append(key).append("\":");
        }

        private static String escape(String value) {
            final StringBuilder escaped = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                appendEscaped(escaped, value.charAt(i));
            }
            return escaped.toString();
        }

        private static void appendEscaped(StringBuilder escaped, char c) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
    }
}
