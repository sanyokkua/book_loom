package ua.bookloom.pipeline.run;

import java.util.List;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.heal.LoopSettings;

/**
 * The chunk being decided: the unit's work and the chunk's own items, its drafts and batch results, the glossary it
 * read, its loop settings, the draft step gated through its protected spans, and the unit's budget an oversized
 * segment is split against.
 */
record Current(
        WorkList work,
        List<WorkItem> items,
        ChunkDrafts drafts,
        ChunkBatches batches,
        ChunkContext context,
        PromptRequests requests,
        LoopSettings loop,
        SegmentTranslator translator,
        int budget) {

    /** The chunk's start: nothing drafted or batched yet, the context, loop and draft step the prompts hold. */
    static Current of(
            final WorkList work, final List<WorkItem> items, final PromptRequests requests, final int budget) {
        return new Current(
                work,
                items,
                new ChunkDrafts(),
                new ChunkBatches(),
                requests.context(),
                requests,
                requests.loopSettings(),
                requests.translator(),
                budget);
    }
}
