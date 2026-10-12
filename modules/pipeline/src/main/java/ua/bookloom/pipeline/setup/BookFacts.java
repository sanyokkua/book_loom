package ua.bookloom.pipeline.setup;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.pipeline.DisplayText;

/**
 * What the book itself says about itself, as the brief calls read it.
 *
 * @param title the metadata title, or null when the book has none
 * @param author the metadata author, or null when the book has none
 * @param year the four-digit year in the metadata date, or null when there is none
 * @param publisher the metadata publisher, or null when there is none
 * @param description the book's description cut to {@link #DESCRIPTION_LIMIT} characters, or null when it has none
 */
@Slf4j
public record BookFacts(
        @Nullable String title,
        @Nullable String author,
        @Nullable Integer year,
        @Nullable String publisher,
        @Nullable String description) {

    /** The most characters of description a call is shown. */
    public static final int DESCRIPTION_LIMIT = 600;

    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    /**
     * Reads the facts of a parsed book.
     *
     * @param document the book; never null
     * @return its facts, each absent one null; never null
     */
    public static BookFacts of(final Document document) {
        Objects.requireNonNull(document, "document");
        final Map<String, String> metadata = document.metadata();
        final BookFacts facts = new BookFacts(
                valueOf(metadata, MetadataKey.TITLE),
                valueOf(metadata, MetadataKey.AUTHOR),
                yearOf(valueOf(metadata, MetadataKey.DATE)),
                valueOf(metadata, MetadataKey.PUBLISHER),
                descriptionOf(document));
        log.debug(
                "Book facts of {}: title={} author={} year={} publisher={} descriptionChars={}",
                document.id(),
                facts.title() != null,
                facts.author() != null,
                facts.year(),
                facts.publisher() != null,
                facts.description() == null ? 0 : facts.description().length());
        return facts;
    }

    private static @Nullable String valueOf(final Map<String, String> metadata, final MetadataKey key) {
        final String value = metadata.get(key.key());
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static @Nullable Integer yearOf(final @Nullable String date) {
        return date != null && YEAR.matcher(date).matches() ? Integer.valueOf(date) : null;
    }

    private static @Nullable String descriptionOf(final Document document) {
        final String joined = document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.kind() == SegmentKind.METADATA_DESCRIPTION)
                .map(segment -> DisplayText.of(segment.masked()))
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" "));
        return joined.isEmpty() ? null : cut(joined);
    }

    /** Cuts at the last whitespace within the limit so no word is split; a text without one is cut hard. */
    private static String cut(final String text) {
        if (text.length() <= DESCRIPTION_LIMIT) {
            return text;
        }
        int end = DESCRIPTION_LIMIT;
        while (end > 0 && !Character.isWhitespace(text.charAt(end))) {
            end--;
        }
        return end == 0
                ? text.substring(0, DESCRIPTION_LIMIT)
                : text.substring(0, end).stripTrailing();
    }
}
