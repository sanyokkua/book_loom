package ua.bookloom.pipeline.context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.QuoteStyle;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.glossary.GlossaryNames;

/**
 * The book map as one call reads it (design D8): the glossary's lemma entries with their forms, the recurring terms
 * with the forms the book writes them in, and the Book Brief's policy keys. Built at run start and again after a
 * glossary or brief change; the window's map lines (task 11.3) are chosen from it.
 *
 * @param entries the glossary's lemma entries; copied
 * @param terms the recurring terms; copied
 * @param apostrophe the brief's apostrophe, or null for the target file's default
 * @param quoteStyle the brief's quotation marks, or null for the target file's default
 * @param defaultAddress the brief's default address, or null when not declared
 * @param conventions the brief's declared conventions by name; copied
 * @param glossFirstUse for each entry the person chose to gloss at first use, by entry id, the id of the book's first
 *     body segment that names it in reading order; copied, empty when no entry is glossed
 */
public record BookMap(
        List<GlossaryEntry> entries,
        List<LexiconEntry> terms,
        @Nullable String apostrophe,
        @Nullable QuoteStyle quoteStyle,
        @Nullable Address defaultAddress,
        Map<String, String> conventions,
        Map<String, String> glossFirstUse) {

    /** Copies the parts. */
    public BookMap {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        terms = List.copyOf(Objects.requireNonNull(terms, "terms"));
        conventions = Map.copyOf(Objects.requireNonNull(conventions, "conventions"));
        glossFirstUse = Map.copyOf(Objects.requireNonNull(glossFirstUse, "glossFirstUse"));
    }

    /**
     * The map of a project now.
     *
     * @param entries the non-null glossary entries
     * @param terms the non-null recurring terms
     * @param brief the non-null Book Brief whose policy keys the map carries
     * @return the map; never null
     */
    public static BookMap of(final List<GlossaryEntry> entries, final List<LexiconEntry> terms, final BookBrief brief) {
        return withFirstUse(entries, terms, brief, Map.of());
    }

    /**
     * The map of a project now, with the first use of each glossed name read from the book.
     *
     * @param entries the non-null glossary entries
     * @param terms the non-null recurring terms
     * @param brief the non-null Book Brief whose policy keys the map carries
     * @param units the non-null units of the book in reading order
     * @return the map; never null
     */
    public static BookMap of(
            final List<GlossaryEntry> entries,
            final List<LexiconEntry> terms,
            final BookBrief brief,
            final List<Unit> units) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(units, "units");
        return withFirstUse(entries, terms, brief, glossFirstUse(entries, WindowAssembler.readingOrder(units)));
    }

    private static BookMap withFirstUse(
            final List<GlossaryEntry> entries,
            final List<LexiconEntry> terms,
            final BookBrief brief,
            final Map<String, String> glossFirstUse) {
        Objects.requireNonNull(brief, "brief");
        return new BookMap(
                entries,
                terms,
                brief.apostrophe(),
                brief.quoteStyle(),
                brief.defaultAddress(),
                brief.conventions(),
                glossFirstUse);
    }

    // A name glossed where it first appears in the story: the first body segment naming it, in reading order.
    private static Map<String, String> glossFirstUse(final List<GlossaryEntry> entries, final List<Segment> reading) {
        final Map<String, String> firstUse = new LinkedHashMap<>();
        entries.stream()
                .filter(BookMap::isGlossed)
                .forEach(entry -> reading.stream()
                        .filter(segment -> GlossaryNames.isNamedIn(entry, Tokens.replace(segment.masked(), " ")))
                        .findFirst()
                        .ifPresent(segment -> firstUse.put(entry.id(), segment.id())));
        return firstUse;
    }

    /**
     * Whether the person chose to gloss an entry's meaning at its first use, and there is a meaning to gloss.
     *
     * @param entry the non-null entry
     * @return {@code true} if its line carries the gloss once, {@code false} otherwise
     */
    static boolean isGlossed(final GlossaryEntry entry) {
        return entry.meaningChoice() == MeaningChoice.TRANSCRIBE_GLOSS && entry.meaning() != null;
    }
}
