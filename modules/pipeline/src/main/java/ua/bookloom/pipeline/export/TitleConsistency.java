package ua.bookloom.pipeline.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;

/**
 * The book's one translated title and author, taken from the metadata segments (the person's edit, else the model's
 * answer) and laid over every other place the same source text stands alone as a title, heading or navigation label
 * — the title page's heading, the NCX {@code docTitle}, the table-of-contents entry, a section's {@code <title>}. The
 * model translated each of those on its own and rendered one title three ways; code makes them one.
 *
 * <p>A segment that holds anything besides the whole text (a longer heading, inline markup) is left to its own
 * translation, and a kind the brief keeps as source is never replaced.
 */
@Slf4j
final class TitleConsistency {

    private static final Set<SegmentKind> PLACES =
            Set.of(SegmentKind.HEADING, SegmentKind.TITLE, SegmentKind.NAV_LABEL, SegmentKind.METADATA_TITLE);
    private static final TitleConsistency NONE = new TitleConsistency(List.of());

    private final List<Entry> entries;

    private TitleConsistency(final List<Entry> entries) {
        this.entries = entries;
    }

    /** What stands in a source text's place: the plain target written into the file and its masked form. */
    record Replacement(String target, String masked) {}

    private record Entry(String source, Replacement replacement) {}

    /**
     * Reads the translated title and first author out of the stored records.
     *
     * @param opened the opened book, whose auxiliary unit holds the metadata segments
     * @param byId every stored record by segment id
     * @param keptKinds the auxiliary kinds the brief keeps as source
     * @return the consistency to apply; empty-handed when neither was translated
     */
    static TitleConsistency of(
            final Document opened, final Map<String, SegmentRecord> byId, final Set<SegmentKind> keptKinds) {
        final List<Entry> found = new ArrayList<>();
        for (final SegmentKind kind : List.of(SegmentKind.METADATA_TITLE, SegmentKind.METADATA_AUTHOR)) {
            firstOf(opened, kind)
                    .flatMap(segment -> entryOf(segment, byId.get(segment.id()), keptKinds))
                    .ifPresent(found::add);
        }
        log.debug("title consistency entries={}", found.size());
        return found.isEmpty() ? NONE : new TitleConsistency(found);
    }

    /**
     * The replacement for a segment, when its whole text is the title or the author.
     *
     * @param segment the segment about to be written
     * @param record its stored record
     * @return the replacement, or empty when the segment is not such a place or was not decided
     */
    Optional<Replacement> forSegment(final Segment segment, final SegmentRecord record) {
        if (entries.isEmpty()
                || !PLACES.contains(segment.kind())
                || record.status() == SegmentStatus.PENDING
                || hasTokens(segment.masked())) {
            return Optional.empty();
        }
        final String text = DisplayText.of(segment.masked());
        return entries.stream()
                .filter(entry -> entry.source().equalsIgnoreCase(text))
                .map(Entry::replacement)
                .findFirst();
    }

    private static Optional<Segment> firstOf(final Document opened, final SegmentKind kind) {
        return opened.units().stream()
                .filter(Unit::isAuxiliary)
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.kind() == kind)
                .findFirst();
    }

    private static Optional<Entry> entryOf(
            final Segment segment, @Nullable final SegmentRecord record, final Set<SegmentKind> keptKinds) {
        if (record == null
                || record.isKeptAsSource(keptKinds)
                || record.status() == SegmentStatus.PENDING
                || hasTokens(segment.masked())) {
            return Optional.empty();
        }
        final String masked = record.userTarget() != null ? record.maskedUserTarget() : record.maskedMachineTarget();
        final String target = record.effectiveTarget().orElse(null);
        if (masked == null || target == null || target.isBlank() || hasTokens(masked)) {
            return Optional.empty();
        }
        return Optional.of(new Entry(DisplayText.of(segment.masked()), new Replacement(target, masked)));
    }

    private static boolean hasTokens(final String masked) {
        return !Tokens.inOrder(masked).isEmpty();
    }
}
