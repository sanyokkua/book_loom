package ua.bookloom.pipeline.run;

import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Puts the MDC key {@code segment} on every log line of a span, so the lines from a segment's start to its decision all
 * name it. Spans nest: a span of no segment inside a segment's span — the unit-end summary call — clears the key for
 * its own lines, and each span gives back the value it found when it ends, however it ends.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentLogContext {

    static final String KEY = "segment";

    /**
     * Runs the body with the key set to the segment.
     *
     * @param segmentId the segment the span is for, or {@code null} for a span of no single segment, whose lines carry
     *     no segment
     * @param body the span's work
     * @return what the body returned
     */
    static <T> T within(@Nullable final String segmentId, final Supplier<T> body) {
        final String outer = MDC.get(KEY);
        put(segmentId);
        try {
            return body.get();
        } finally {
            put(outer);
        }
    }

    private static void put(@Nullable final String segmentId) {
        if (segmentId == null) {
            MDC.remove(KEY);
        } else {
            MDC.put(KEY, segmentId);
        }
    }
}
