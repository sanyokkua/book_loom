package ua.bookloom.pipeline.memory;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.qa.TextSimilarity;
import ua.bookloom.util.hash.HashUtil;

/**
 * Reads and builds translation-memory entries for one project. An entry is keyed by the segment's source hash and by
 * the source hashes of its two neighbours in the unit, so a stored target is reused blindly only where the
 * surrounding text says the same thing — compared by text, not by position, so a passage repeated in another chapter
 * still matches. Reading never writes; the run stores the entries it builds when it accepts a segment.
 */
@Slf4j
public final class TranslationMemory {

    private static final String UNIT_START = "⟦BOS⟧";
    private static final String UNIT_END = "⟦EOS⟧";
    private static final String KEY_SEPARATOR = "|";
    private static final int PERCENT = 100;
    private static final int SIMILARITY_PERCENT = 85;
    private static final double SIMILARITY_THRESHOLD = SIMILARITY_PERCENT / (double) PERCENT;

    private final TmRepository repository;
    private final String projectId;

    /**
     * Creates the memory of one project.
     *
     * @param repository where the run keeps entries; never null
     * @param projectId the project whose entries are read; never null
     */
    public TranslationMemory(final TmRepository repository, final String projectId) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    /**
     * The key of the context a segment sits in.
     *
     * @param segment a segment of {@code unitSegments}; never null
     * @param unitSegments every segment of the segment's unit, in document order; never null
     * @return the SHA-256 hex of the previous and the next neighbour's source hash, a unit edge counting as a marker
     * @throws IllegalArgumentException when {@code segment} is not one of {@code unitSegments}
     */
    public String contextKey(final Segment segment, final List<Segment> unitSegments) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(unitSegments, "unitSegments");
        final int index = indexOf(segment, unitSegments);
        final String previous =
                index == 0 ? UNIT_START : unitSegments.get(index - 1).sourceHash();
        final String next = index == unitSegments.size() - 1
                ? UNIT_END
                : unitSegments.get(index + 1).sourceHash();
        return HashUtil.sha256Hex((previous + KEY_SEPARATOR + next).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds the entry that records an accepted segment.
     *
     * @param segment the accepted segment, one of {@code unitSegments}; never null
     * @param unitSegments every segment of the segment's unit, in document order; never null
     * @param maskedTarget the accepted target in masked form, so a reuse restores through the same gate; never null
     * @return the entry to store; nothing is written here
     */
    public TmEntry entryFor(final Segment segment, final List<Segment> unitSegments, final String maskedTarget) {
        Objects.requireNonNull(maskedTarget, "maskedTarget");
        final String contextKey = contextKey(segment, unitSegments);
        log.debug("TM entry built segment={}", segment.id());
        return new TmEntry(
                segment.sourceHash() + ":" + contextKey,
                projectId,
                segment.sourceHash(),
                contextKey,
                DisplayText.of(segment.masked()),
                maskedTarget);
    }

    /**
     * Looks up what the memory holds for a segment. A repository failure is already logged where it arose and
     * degrades to an empty offer, since the memory only ever helps a draft.
     *
     * @param segment the segment about to be drafted, one of {@code unitSegments}; never null
     * @param unitSegments every segment of the segment's unit, in document order; never null
     * @return the entry to reuse, the same-source hints and the near-match suggestions
     */
    public TmLookup lookup(final Segment segment, final List<Segment> unitSegments) {
        final String contextKey = contextKey(segment, unitSegments);
        final TmEntry reuse = reuseFor(segment, contextKey);
        final List<TmEntry> sameSource = exactFor(segment);
        final List<TmEntry> hints = sameSource.stream()
                .filter(entry -> !entry.contextKey().equals(contextKey))
                .toList();
        final List<TmEntry> suggestions = suggestionsFor(segment, sameSource);
        log.debug(
                "TM lookup segment={} reuse={} hints={} suggestions={}",
                segment.id(),
                reuse != null,
                hints.size(),
                suggestions.size());
        return new TmLookup(reuse, hints, suggestions);
    }

    private @Nullable TmEntry reuseFor(final Segment segment, final String contextKey) {
        return dataOr(repository.context(projectId, segment.sourceHash(), contextKey), Optional.<TmEntry>empty())
                .orElse(null);
    }

    private List<TmEntry> exactFor(final Segment segment) {
        return dataOr(repository.exact(projectId, segment.sourceHash()), List.of());
    }

    private List<TmEntry> suggestionsFor(final Segment segment, final List<TmEntry> sameSource) {
        final String display = DisplayText.of(segment.masked());
        final int length = display.codePointCount(0, display.length());
        if (length == 0) {
            log.debug("TM suggestions skipped segment={} reason=no display text", segment.id());
            return List.of();
        }
        final int min = ceilPercent(length);
        final int max = length * PERCENT / SIMILARITY_PERCENT;
        final Set<String> excluded = sameSource.stream().map(TmEntry::id).collect(Collectors.toSet());
        log.debug("TM candidates segment={} band=[{}, {}]", segment.id(), min, max);
        return dataOr(repository.candidates(projectId, min, max), List.<TmEntry>of()).stream()
                .filter(entry -> !excluded.contains(entry.id()))
                .map(entry -> new Scored(entry, TextSimilarity.similarity(display, entry.sourceInner())))
                .filter(scored -> scored.similarity() >= SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(Scored::similarity).reversed())
                .map(Scored::entry)
                .toList();
    }

    private static <T> T dataOr(final Result<T> result, final T fallback) {
        final AppError error = result.error();
        if (error != null) {
            log.debug("TM read degraded to an empty answer code={}", error.code());
            return fallback;
        }
        return Objects.requireNonNull(result.data());
    }

    private static int ceilPercent(final int length) {
        return (length * SIMILARITY_PERCENT + PERCENT - 1) / PERCENT;
    }

    private static int indexOf(final Segment segment, final List<Segment> unitSegments) {
        for (int i = 0; i < unitSegments.size(); i++) {
            if (unitSegments.get(i).id().equals(segment.id())) {
                return i;
            }
        }
        throw new IllegalArgumentException("segment " + segment.id() + " is not in its unit");
    }

    private record Scored(TmEntry entry, double similarity) {}
}
