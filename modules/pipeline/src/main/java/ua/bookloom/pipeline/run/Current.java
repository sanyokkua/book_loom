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
        LoopSettings loop,
        SegmentTranslator translator,
        int budget) {}
