package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.context.WindowBudgets;
import ua.bookloom.pipeline.project.ChapterBoundaries;

/**
 * Packs a unit's pending segments into chunks of at most the current-part budget of the run model's tier (design D8),
 * the one token limit a chunk, a batch and an oversized segment's pieces are held to. A chunk also ends where a
 * chapter ends, so no batch, which never leaves its chunk, holds two chapters (design D10). The window is latched
 * again as each chunk is entered (a window the person saved while the run went); it sizes the calls, not the chunks,
 * so the packing stands. Used from the job thread only.
 */
@Slf4j
final class ChunkPlanner {

    /**
     * The chunks a unit's remaining segments are packed into.
     *
     * @param chunks the chunks, in document order
     * @param budget the tokens one chunk may hold
     */
    record Plan(List<Chunk> chunks, int budget) {}

    private final RunWindow window;
    private final int budget;
    private final ChapterBoundaries chapters;
    private RunSettings settings;

    ChunkPlanner(
            final RunSettings settings,
            final RunWindow window,
            final ModelProfile.Tier tier,
            final ChapterBoundaries chapters) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.window = Objects.requireNonNull(window, "window");
        this.chapters = Objects.requireNonNull(chapters, "chapters");
        this.budget = WindowBudgets.currentPart(Objects.requireNonNull(tier, "tier"));
        log.debug("Chunk budget tier={} currentPart={}", tier, budget);
    }

    /** The run's settings as sized for the window latched at the last chunk entry. */
    RunSettings settings() {
        return settings;
    }

    /**
     * The chunks of a unit, one at a time. The window is latched when the unit starts and again as each later chunk is
     * entered.
     *
     * @param items the unit's pending items, in document order
     * @return the cursor over the unit's chunks
     */
    Walk walk(final List<WorkItem> items) {
        applyWindow(window.enterChunk());
        return new Walk(items, plan(items));
    }

    /**
     * One chunk about to run.
     *
     * @param chunk the chunk
     * @param items the items it holds
     * @param number its place in the unit, counting from 1
     * @param total the number of chunks the unit has
     * @param budget the tokens one chunk may hold
     */
    record Next(Chunk chunk, List<WorkItem> items, int number, int total, int budget) {}

    /** The cursor of one unit; job-thread only. */
    final class Walk {

        private final List<WorkItem> items;
        private final Plan plan;
        private int offset;
        private int done;

        private Walk(final List<WorkItem> items, final Plan plan) {
            this.items = items;
            this.plan = plan;
        }

        boolean hasNext() {
            return offset < items.size();
        }

        Next next() {
            if (done > 0) {
                applyWindow(window.enterChunk());
            }
            final Chunk chunk = plan.chunks().get(done);
            final int size = chunk.segments().size();
            final Next next = new Next(
                    chunk,
                    items.subList(offset, offset + size),
                    done + 1,
                    plan.chunks().size(),
                    plan.budget());
            offset += size;
            done++;
            return next;
        }
    }

    private void applyWindow(final int entered) {
        if (entered != settings.window()) {
            log.debug("Chunk entered at another window before={} after={}", settings.window(), entered);
            settings = settings.withWindow(entered);
        }
    }

    private Plan plan(final List<WorkItem> items) {
        final List<Segment> segments = items.stream().map(WorkItem::segment).toList();
        final List<List<Segment>> parts = chapters.split(segments);
        final List<Chunk> chunks = parts.stream()
                .flatMap(part -> ChunkPacker.pack(part, settings.frame().sourceLanguage(), budget).stream())
                .toList();
        log.debug(
                "Packed unit unitId={} budget={} window={} chapterParts={} chunkSizes={}",
                segments.getFirst().unit(),
                budget,
                settings.window(),
                parts.size(),
                chunks.stream().map(chunk -> chunk.segments().size()).toList());
        return new Plan(chunks, budget);
    }
}
