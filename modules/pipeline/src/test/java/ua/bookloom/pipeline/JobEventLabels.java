package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.pipeline.StageStarted;

/** A short text per job event, for tests that assert an exact event order. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JobEventLabels {

    static String label(final JobEvent event) {
        return switch (event) {
            case StageStarted started -> "StageStarted " + started.stage();
            case SegmentStarted started -> "SegmentStarted " + started.segmentId();
            case ModelCallStarted started -> "ModelCallStarted " + started.kind() + " " + started.segmentId();
            case ModelCallFinished finished -> "ModelCallFinished " + finished.kind() + " " + finished.segmentId();
            case SegmentDrafted drafted -> "SegmentDrafted " + drafted.segmentId();
            case SegmentDecided decided ->
                "SegmentDecided " + decided.segmentId() + " " + decided.status() + " pending="
                        + decided.progress().pending();
            case MemoryUpdated updated -> "MemoryUpdated " + updated.kind();
            case Finished finished -> "Finished " + finished.report().end();
            case CallSnapshotUpdated updated ->
                "CallSnapshotUpdated " + updated.snapshot().callId() + " "
                        + updated.snapshot().state() + " outcomes="
                        + updated.snapshot().outcomes().size();
            default -> event.getClass().getSimpleName();
        };
    }
}
