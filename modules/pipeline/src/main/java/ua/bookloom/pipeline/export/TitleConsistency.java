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
import ua.bookloom.pipeline.checks.QuoteConventions;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * The book's one translated title and author, taken from the metadata segments (the person's edit, else the model's
 * answer) and laid over every other place the same source text stands alone as a title, heading or navigation label
 * — the title page's heading, the NCX {@code docTitle}, the table-of-contents entry, a section's {@code <title>}. The
 * model translated each of those on its own and rendered one title three ways; code makes them one.
 *
 * <p>A segment that holds the title and/or the author with only spaces, punctuation and quote marks around and between
 * them ({@code Mara Voss, “Amber Tide”}) is composed from the decided renderings: the separators stay as written, the
 * quote marks around a title become the target language's own, and an author is never quoted. A segment that holds
 * anything else (a longer heading, inline markup) is left to its own translation, and a kind the brief keeps as source
 * is never replaced.
 */
@Slf4j
final class TitleConsistency {

    private static final Set<SegmentKind> PLACES =
            Set.of(SegmentKind.HEADING, SegmentKind.TITLE, SegmentKind.NAV_LABEL, SegmentKind.METADATA_TITLE);

    private static final String QUOTE_CHARS = "\"'“”‘’«»„‟‹›";
    private static final QuotePair DEFAULT_QUOTES = new QuotePair('“', '”');
    private static final TitleConsistency NONE = new TitleConsistency(List.of(), DEFAULT_QUOTES);

    private final List<Entry> entries;
    private final QuotePair quotes;

    private TitleConsistency(final List<Entry> entries, final QuotePair quotes) {
        this.entries = entries;
        this.quotes = quotes;
    }

    /** What stands in a source text's place: the plain target written into the file and its masked form. */
    record Replacement(String target, String masked) {}

    private record Entry(String source, Replacement replacement, boolean isTitle) {}

    /**
     * Reads the translated title and first author out of the stored records.
     *
     * @param opened the opened book, whose auxiliary unit holds the metadata segments
     * @param byId every stored record by segment id
     * @param keptKinds the auxiliary kinds the brief keeps as source
     * @param targetLanguage the language tag whose quote marks wrap a composed title
     * @return the consistency to apply; empty-handed when neither was translated
     */
    static TitleConsistency of(
            final Document opened,
            final Map<String, SegmentRecord> byId,
            final Set<SegmentKind> keptKinds,
            final String targetLanguage) {
        final List<Entry> found = new ArrayList<>();
        for (final SegmentKind kind : List.of(SegmentKind.METADATA_TITLE, SegmentKind.METADATA_AUTHOR)) {
            firstOf(opened, kind)
                    .flatMap(segment -> entryOf(segment, kind, byId.get(segment.id()), keptKinds))
                    .ifPresent(found::add);
        }
        log.debug("title consistency entries={}", found.size());
        final QuotePair quotes =
                QuoteConventions.ownLine(targetLanguage).map(List::getFirst).orElse(DEFAULT_QUOTES);
        return found.isEmpty() ? NONE : new TitleConsistency(found, quotes);
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
                .findFirst()
                .or(() -> composed(text));
    }

    // Walks the text as separator, name, separator, name…; any letter that is not one of the decided names ends it.
    private Optional<Replacement> composed(final String text) {
        final StringBuilder out = new StringBuilder();
        int at = 0;
        boolean isAnyName = false;
        while (at < text.length()) {
            final int nameAt = firstLetterOrDigit(text, at);
            out.append(separator(text.substring(at, nameAt == -1 ? text.length() : nameAt), nameAt == -1));
            if (nameAt == -1) {
                break;
            }
            final Optional<Entry> entry = entryAt(text, nameAt);
            if (entry.isEmpty()) {
                return Optional.empty();
            }
            final int end = nameAt + entry.get().source().length();
            final boolean isQuoted = entry.get().isTitle() && isQuotedAround(text, nameAt, end);
            out.append(
                    isQuoted
                            ? quotes.open() + entry.get().replacement().target() + quotes.close()
                            : entry.get().replacement().target());
            isAnyName = true;
            at = end;
        }
        log.debug("title composed from a {}-character text found={}", text.length(), isAnyName);
        log.trace("title composed from '{}'", text);
        return isAnyName ? Optional.of(new Replacement(out.toString(), out.toString())) : Optional.empty();
    }

    private Optional<Entry> entryAt(final String text, final int at) {
        return entries.stream()
                .filter(entry -> text.regionMatches(
                        true, at, entry.source(), 0, entry.source().length()))
                .filter(entry -> isWordEnd(text, at + entry.source().length()))
                .findFirst();
    }

    private static boolean isWordEnd(final String text, final int end) {
        return end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
    }

    private static int firstLetterOrDigit(final String text, final int from) {
        for (int i = from; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    // A title is quoted when a quote mark touches it: right before its first letter or right after its last. A quote
    // elsewhere in a separator belongs to the author's name or to nothing, and is dropped with the separator's marks.
    private static boolean isQuotedAround(final String text, final int start, final int end) {
        return (start > 0 && QUOTE_CHARS.indexOf(text.charAt(start - 1)) >= 0)
                || (end < text.length() && QUOTE_CHARS.indexOf(text.charAt(end)) >= 0);
    }

    // A separator keeps its spaces and punctuation but loses its quote marks; the edges of the heading lose the
    // spaces the marks left behind.
    private static String separator(final String part, final boolean isLast) {
        final String kept = part.chars()
                .filter(c -> QUOTE_CHARS.indexOf(c) < 0)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return isLast ? kept.stripTrailing() : kept;
    }

    private static Optional<Segment> firstOf(final Document opened, final SegmentKind kind) {
        return opened.units().stream()
                .filter(Unit::isAuxiliary)
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.kind() == kind)
                .findFirst();
    }

    private static Optional<Entry> entryOf(
            final Segment segment,
            final SegmentKind kind,
            @Nullable final SegmentRecord record,
            final Set<SegmentKind> keptKinds) {
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
        return Optional.of(new Entry(
                DisplayText.of(segment.masked()), new Replacement(target, masked), kind == SegmentKind.METADATA_TITLE));
    }

    private static boolean hasTokens(final String masked) {
        return !Tokens.inOrder(masked).isEmpty();
    }
}
