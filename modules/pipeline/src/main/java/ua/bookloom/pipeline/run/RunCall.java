package ua.bookloom.pipeline.run;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.RequestSummary;

/**
 * What every attempt of one call through {@link JobModelCalls} shares.
 *
 * @param kind what the call is for
 * @param segmentIds the segments it is about
 * @param summary the request's size as first built; an attempt's own cap may be lower
 */
record RunCall(CallKind kind, List<String> segmentIds, RequestSummary summary) {

    /** The one segment the call is about, or null when it names none or several. */
    @Nullable
    String soleSegment() {
        return segmentIds.size() == 1 ? segmentIds.getFirst() : null;
    }
}
