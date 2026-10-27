package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.context.ContextBudget;

/**
 * Packs a unit's pending segments into chunks for the run's current window, and packs the segments not yet run again
 * when a chunk is entered at another window (a window the person saved while the run went). Used from the job thread
 * only.
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
    private RunSettings settings;

    ChunkPlanner(final RunSettings settings, final RunWindow window) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.window = Objects.requireNonNull(window, "window");
    }

    /** The run's settings as sized for the window latched at the last chunk entry. */
    RunSettings settings() {
        return settings;
    }

    /**
     * The chunks of a unit, one at a time. The window is latched when the unit starts and again as each later chunk is
     * entered; when it changed, the items still waiting are packed again for it.
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
     * @param total the number of chunks the unit has now
     * @param budget the tokens one chunk may hold
     */
    record Next(Chunk chunk, List<WorkItem> items, int number, int total, int budget) {}

    /** The cursor of one unit; job-thread only. */
    final class Walk {

        private final List<WorkItem> items;
        private Plan plan;
        private int offset;
        private int done;
        private int index;

        private Walk(final List<WorkItem> items, final Plan plan) {
            this.items = items;
            this.plan = plan;
        }

        boolean hasNext() {
            return offset < items.size();
        }

        Next next() {
            if (done > 0) {
                final int entered = window.enterChunk();
                if (entered != settings.window()) {
                    applyWindow(entered);
                    plan = plan(items.subList(offset, items.size()));
                    index = 0;
                    log.debug(
                            "Repacked the rest of the unit done={} chunks={}",
                            done,
                            plan.chunks().size());
                }
            }
            final Chunk chunk = plan.chunks().get(index);
            final int size = chunk.segments().size();
            final Next next = new Next(
                    chunk,
                    items.subList(offset, offset + size),
                    done + 1,
                    done + plan.chunks().size() - index,
                    plan.budget());
            offset += size;
            done++;
            index++;
            return next;
        }
    }

    private void applyWindow(final int entered) {
        if (entered != settings.window()) {
            settings = settings.withWindow(entered);
        }
    }

    private Plan plan(final List<WorkItem> items) {
        final List<Segment> segments = items.stream().map(WorkItem::segment).toList();
        final ContextBudget budgeted = ChunkBudget.budget(settings.frame(), settings.window());
        final int budget = budgeted.chunkTokens();
        final int cap = settings.dial().chunkCap(settings.mode());
        final List<Chunk> chunks = ChunkPacker.pack(segments, settings.frame().sourceLanguage(), budget, cap);
        log.debug(
                "Packed unit unitId={} budget={} {} cap={} chunkSizes={}",
                segments.getFirst().unit(),
                budget,
                budgeted.describe(),
                cap,
                chunks.stream().map(chunk -> chunk.segments().size()).toList());
        return new Plan(chunks, budget);
    }
}
