package ua.bookloom.api.pipeline;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Moves a segment through its status machine and reads the review queue, the seam the review panel and the Export
 * screen call — the stored decisions are read only through here.
 */
public interface ReviewDesk {

    /**
     * Accepts a segment: FLAGGED becomes ACCEPTED, and accepting an already-ACCEPTED segment is a confirmation with
     * no change. Any other status is refused with {@code validation}.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the segment's stored record after the action
     */
    Result<SegmentRecord> accept(String projectId, String segmentId);

    /**
     * Saves the person's edit as the segment's user target.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @param maskedText the non-null edited text, in masked form
     * @return the segment's stored record after the action
     */
    Result<SegmentRecord> saveEdit(String projectId, String segmentId, String maskedText);

    /**
     * Discards the person's edit, restoring the machine target.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the segment's stored record after the action
     */
    Result<SegmentRecord> revert(String projectId, String segmentId);

    /**
     * Retries a segment's translation, replaying the context it first saw.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @param note an optional instruction added to the retry, or null for none
     * @param lowerTemperature whether the retry should use a lower sampling temperature
     * @param model the non-null model to call
     * @return the segment's stored record after the action
     */
    Result<SegmentRecord> retry(
            String projectId, String segmentId, @Nullable String note, boolean lowerTemperature, ChatModel model);

    /**
     * Skips a segment: nothing about it changes and it is not marked reviewed, and the record answered is the next
     * flagged segment the queue moves on to.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the next flagged segment's stored record, or the skipped segment's own when no other is flagged
     */
    Result<SegmentRecord> skip(String projectId, String segmentId);

    /**
     * Applies a pending backward-revision proposal as the person's own edit.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the segment's stored record after the action
     */
    Result<SegmentRecord> acceptProposal(String projectId, String segmentId);

    /**
     * Lists the segments matching a filter, in document order.
     *
     * @param projectId the non-null project id
     * @param filter the non-null filter to apply
     * @return the matching segment views, in document order
     */
    Result<List<SegmentView>> queue(String projectId, ReviewFilter filter);

    /**
     * Reads one segment's current view.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the segment's current view
     */
    Result<SegmentView> segment(String projectId, String segmentId);

    /**
     * Computes the project's review counts.
     *
     * @param projectId the non-null project id
     * @return the computed counts
     */
    Result<ReviewCounts> counts(String projectId);
}
