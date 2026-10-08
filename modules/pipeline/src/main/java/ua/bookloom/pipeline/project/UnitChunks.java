package ua.bookloom.pipeline.project;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.VerbatimRule;

/**
 * The one packing of a unit into chunks that both the plan and the Structure screen's segment listing read, so the two
 * can never disagree on which segments are translated or where a chunk ends. It is the run's packing as far as it can
 * be known before a model is chosen: the run sizes a chunk by the window the model reports, which at the default
 * window ({@link ua.bookloom.pipeline.context.ContextBudget#DEFAULT_WINDOW}) reaches the
 * {@link TokenBudget#MAX_CHUNK_TOKENS} ceiling packed by here, and caps it by the dial as an unattended or guided run
 * does. A model with a smaller window, or a Manual review, packs smaller chunks, so the screen calls these "planned".
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UnitChunks {

    /**
     * Packs the segments a run would translate in a unit.
     *
     * @param unit the unit to pack
     * @param brief the brief whose kept kinds, source language and dial decide the packing
     * @return the chunks in order; empty when the unit has nothing to translate
     */
    static List<Chunk> pack(final Unit unit, final BookBrief brief) {
        final Set<SegmentKind> kept = brief.alsoTranslate().keptKinds();
        final List<Segment> translated = unit.segments().stream()
                .filter(segment -> !isKeptAsSource(unit, segment, kept))
                .toList();
        return ChunkPacker.pack(
                translated,
                brief.sourceLanguage(),
                TokenBudget.MAX_CHUNK_TOKENS,
                DialParameters.of(brief.dial()).chunkCap());
    }

    /**
     * Describes every segment of a unit with its planned chunk.
     *
     * @param unit the unit to describe
     * @param brief the brief the packing follows
     * @param locators the book's segment locators
     * @return one preview per segment, in document order
     */
    static List<SegmentPreview> previews(
            final Unit unit, final BookBrief brief, final Map<String, SegmentLocator> locators) {
        Objects.requireNonNull(locators, "locators");
        final Set<SegmentKind> kept = brief.alsoTranslate().keptKinds();
        final List<Chunk> chunks = pack(unit, brief);
        final Map<String, Chunk> chunkOf = new HashMap<>();
        final Map<String, Integer> indexOf = new HashMap<>();
        for (int index = 0; index < chunks.size(); index++) {
            for (final Segment segment : chunks.get(index).segments()) {
                chunkOf.put(segment.id(), chunks.get(index));
                indexOf.put(segment.id(), index + 1);
            }
        }
        final List<SegmentPreview> previews = unit.segments().stream()
                .map(segment -> preview(
                        segment,
                        locators.get(segment.id()),
                        isKeptAsSource(unit, segment, kept),
                        brief.sourceLanguage(),
                        indexOf.getOrDefault(segment.id(), 0),
                        chunks.size(),
                        chunkOf.get(segment.id())))
                .toList();
        log.debug(
                "previewed unit={} segments={} chunks={} keptKinds={}",
                unit.id(),
                previews.size(),
                chunks.size(),
                kept);
        return previews;
    }

    private static SegmentPreview preview(
            final Segment segment,
            final @Nullable SegmentLocator locator,
            final boolean keptAsSource,
            final @Nullable String sourceLanguage,
            final int chunkIndex,
            final int chunkCount,
            final @Nullable Chunk chunk) {
        final String display = DisplayText.of(segment.masked());
        // A locked glossary name is not known here, so a segment of one reads as translated until the run protects it.
        final boolean verbatim = !keptAsSource && VerbatimRule.match(segment.masked(), false) != null;
        return new SegmentPreview(
                segment.id(),
                locator == null ? segment.id() : locator.text(),
                segment.kind(),
                display,
                segment.masked(),
                TokenEstimator.estimate(segment.masked(), sourceLanguage),
                verbatim,
                keptAsSource,
                chunkIndex,
                chunkIndex == 0 ? 0 : chunkCount,
                chunk != null && chunk.oversized());
    }

    private static boolean isKeptAsSource(final Unit unit, final Segment segment, final Set<SegmentKind> kept) {
        return unit.isAuxiliary() && kept.contains(segment.kind());
    }
}
