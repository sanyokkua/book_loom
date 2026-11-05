package ua.bookloom.pipeline.run;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.heal.DraftOutcome;

/**
 * What a chunk's batch calls have made so far: the drafts a batch answered correctly, waiting for their segment's turn,
 * and the segments a batch has already tried, which are drafted singly from then on whatever the batch made of them.
 * Kept across a pause with the chunk, so a resume never repeats a batch call that answered. A stop drops it.
 *
 * <p>Used from the job thread only, which is why the state is plain collections.
 */
@Slf4j
final class ChunkBatches {

    private final Map<String, DraftOutcome> ready = new HashMap<>();
    private final Map<String, PromptPlan> sent = new HashMap<>();
    private final Set<String> tried = new HashSet<>();
    private final Map<String, TranslateContext> contexts = new HashMap<>();
    private final Set<String> echoed = new HashSet<>();

    /** Whether a batch has dealt with the segment, so no new batch starts at it. */
    boolean hasTried(final String segmentId) {
        return tried.contains(segmentId);
    }

    void tried(final String segmentId) {
        tried.add(segmentId);
    }

    void ready(final String segmentId, final DraftOutcome outcome) {
        ready.put(segmentId, outcome);
    }

    /** The draft a batch made for the segment, handed over once; null when the segment is drafted on its own. */
    @Nullable
    DraftOutcome take(final String segmentId) {
        return ready.remove(segmentId);
    }

    /** Keeps the window and map the segment's batch was shown, which its one repair is planned over. */
    void shown(final String segmentId, final TranslateContext context) {
        contexts.put(segmentId, context);
    }

    /** The window and map the segment's batch was shown, handed over once; null when it was drafted on its own. */
    @Nullable
    TranslateContext takeShown(final String segmentId) {
        return contexts.remove(segmentId);
    }

    /** Notes that the segment's batch answer copied a shown example, so its own draft is that answer's one re-ask. */
    void echoed(final String segmentId) {
        echoed.add(segmentId);
    }

    /** Whether the segment's batch answer copied a shown example. */
    boolean wasEchoed(final String segmentId) {
        return echoed.contains(segmentId);
    }

    /** Keeps the plan the segment's batch sent; null when the batch's answer is not one the plan describes. */
    void sent(final String segmentId, @Nullable final PromptPlan plan) {
        if (plan != null) {
            sent.put(segmentId, plan);
            log.debug(
                    "Sent plan kept segmentId={} blocks={} summaryKeys={}",
                    segmentId,
                    plan.blocks().size(),
                    plan.chapterSummaryKeys().size());
        }
    }

    /** The plan the segment's batch sent, handed over once; null when it was drafted on its own. */
    @Nullable
    PromptPlan takeSent(final String segmentId) {
        return sent.remove(segmentId);
    }
}
