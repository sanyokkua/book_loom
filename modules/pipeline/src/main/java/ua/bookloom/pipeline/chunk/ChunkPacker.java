package ua.bookloom.pipeline.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;

/** Packs one unit's segments into chunks; a chunk never spans units because the caller packs a unit at a time. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChunkPacker {

    /**
     * Packs consecutive segments into chunks, closing a chunk when the next segment would exceed the token budget
     * or the segment cap. A segment that exceeds the budget alone becomes an oversized chunk of its own.
     *
     * @param unitSegments the unit's segments to translate, in document order; never null
     * @param sourceLanguage the source language tag, or null when unknown
     * @param budgetTokens the tokens one chunk may hold
     * @param segmentCap the most segments in one chunk; 1 in Manual review
     * @return the chunks in order; empty when there is no segment
     */
    public static List<Chunk> pack(
            final List<Segment> unitSegments,
            @Nullable final String sourceLanguage,
            final int budgetTokens,
            final int segmentCap) {
        Objects.requireNonNull(unitSegments, "unitSegments");
        final List<Chunk> chunks = new ArrayList<>();
        final List<Segment> open = new ArrayList<>();
        int openTokens = 0;
        for (final Segment segment : unitSegments) {
            final int tokens = TokenEstimator.estimate(segment.masked(), sourceLanguage);
            if (tokens > budgetTokens) {
                close(chunks, open);
                chunks.add(new Chunk(segment.unit(), List.of(segment), true));
                openTokens = 0;
                continue;
            }
            if (!open.isEmpty() && (open.size() >= segmentCap || openTokens + tokens > budgetTokens)) {
                close(chunks, open);
                openTokens = 0;
            }
            open.add(segment);
            openTokens += tokens;
        }
        close(chunks, open);
        logPacked(unitSegments, budgetTokens, segmentCap, chunks);
        return chunks;
    }

    private static void close(final List<Chunk> chunks, final List<Segment> open) {
        if (!open.isEmpty()) {
            chunks.add(new Chunk(open.get(0).unit(), open, false));
            open.clear();
        }
    }

    private static void logPacked(
            final List<Segment> unitSegments, final int budget, final int cap, final List<Chunk> chunks) {
        if (!log.isDebugEnabled() || unitSegments.isEmpty()) {
            return;
        }
        final List<Integer> sizes =
                chunks.stream().map(c -> c.segments().size()).toList();
        final List<String> oversized = chunks.stream()
                .filter(Chunk::oversized)
                .map(c -> c.segments().get(0).id())
                .toList();
        log.debug(
                "packed unit {}: {} segments, budget {}, cap {}, {} chunks {}, oversized {}",
                unitSegments.get(0).unit(),
                unitSegments.size(),
                budget,
                cap,
                chunks.size(),
                sizes,
                oversized);
    }
}
