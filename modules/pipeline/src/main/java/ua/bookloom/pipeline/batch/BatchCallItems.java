package ua.bookloom.pipeline.batch;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallItemStatus;

/** The items a batch call's row shows, read from its reply. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchCallItems {

    /**
     * Reads a reply as the items a call row shows: the expected ids in order, then any extra id. A gate refusal made
     * later by the caller is not an item status, so it is not shown here.
     */
    static List<CallItem> of(final List<String> ids, final List<String> segmentIds, final BatchReply reply) {
        final List<CallItem> expected = new ArrayList<>();
        final List<CallItem> extra = new ArrayList<>();
        for (final ItemOutcome outcome : reply.outcomes()) {
            final int index = ids.indexOf(outcome.id());
            final String target = outcome.target().isBlank() ? null : outcome.target();
            final CallItem item = new CallItem(
                    outcome.id(),
                    index < 0 || index >= segmentIds.size() ? null : segmentIds.get(index),
                    target,
                    CallItemStatus.valueOf(outcome.status().name()),
                    outcome.problems().stream().map(ItemProblem::name).toList());
            (outcome.status() == ItemStatus.EXTRA ? extra : expected).add(item);
        }
        expected.addAll(extra);
        return expected;
    }
}
