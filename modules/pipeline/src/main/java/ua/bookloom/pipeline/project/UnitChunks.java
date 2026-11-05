package ua.bookloom.pipeline.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.WindowBudgets;
import ua.bookloom.pipeline.heal.VerbatimRule;

/**
 * The one packing of a unit into chunks that both the plan and the Structure screen's segment listing read, so the two
 * can never disagree on which segments are translated or where a chunk ends. It is the run's packing as far as it can
 * be known before a model is chosen: the run packs a chunk up to the current-part budget of its model's tier
 * ({@link WindowBudgets#currentPart}), and this packs at the tier of {@link ModelProfile#defaults()}, so a model of
 * another tier packs other chunks and the screen calls these "planned". Like the run's, a chunk ends where a chapter
 * ends ({@link ChapterBoundaries}).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UnitChunks {

    /**
     * Packs the segments a run would translate in a unit.
     *
     * @param unit the unit to pack
     * @param brief the brief whose kept kinds and source language decide the packing
     * @param chapters the book's chapters, where a chunk also ends
     * @return the chunks in order; empty when the unit has nothing to translate
     */
    static List<Chunk> pack(final Unit unit, final BookBrief brief, final ChapterBoundaries chapters) {
        final Set<SegmentKind> kept = brief.alsoTranslate().keptKinds();
        final List<Segment> translated = unit.segments().stream()
                .filter(segment -> !isKeptAsSource(unit, segment, kept))
                .toList();
        final int budget = WindowBudgets.currentPart(ModelProfile.defaults().tier());
        return chapters.split(translated).stream()
                .flatMap(part -> ChunkPacker.pack(part, brief.sourceLanguage(), budget).stream())
                .toList();
    }

    /**
     * Describes every segment of a unit with its planned chunk.
     *
     * @param unit the unit to describe
     * @param brief the brief the packing follows
     * @param locators the book's segment locators
     * @param chapters the book's chapters, where a chunk also ends
     * @return one preview per segment, in document order
     */
    static List<SegmentPreview> previews(
            final Unit unit,
            final BookBrief brief,
            final Map<String, SegmentLocator> locators,
            final ChapterBoundaries chapters) {
        Objects.requireNonNull(locators, "locators");
        final Set<SegmentKind> kept = brief.alsoTranslate().keptKinds();
        final List<Chunk> chunks = pack(unit, brief, chapters);
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

    static BookPlan plan(
            final String projectId, final Document document, final BookBrief brief, final ChapterBoundaries chapters) {
        final Map<String, Integer> perUnit = new LinkedHashMap<>();
        final List<String> oversized = new ArrayList<>();
        for (final Unit unit : document.units()) {
            final List<Chunk> chunks = UnitChunks.pack(unit, brief, chapters);
            perUnit.put(unit.id(), chunks.size());
            chunks.stream()
                    .filter(Chunk::oversized)
                    .forEach(chunk -> oversized.add(chunk.segments().get(0).id()));
        }
        log.debug(
                "plan project={} chunks={} oversized={} keptKinds={}",
                projectId,
                perUnit.values().stream().mapToInt(Integer::intValue).sum(),
                oversized.size(),
                brief.alsoTranslate().keptKinds());
        return new BookPlan(perUnit, oversized);
    }
}
