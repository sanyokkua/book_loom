package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.QuoteStyle;

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
 */
public record BookMap(
        List<GlossaryEntry> entries,
        List<LexiconEntry> terms,
        @Nullable String apostrophe,
        @Nullable QuoteStyle quoteStyle,
        @Nullable Address defaultAddress,
        Map<String, String> conventions) {

    /** Copies the parts. */
    public BookMap {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        terms = List.copyOf(Objects.requireNonNull(terms, "terms"));
        conventions = Map.copyOf(Objects.requireNonNull(conventions, "conventions"));
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
        Objects.requireNonNull(brief, "brief");
        return new BookMap(
                entries, terms, brief.apostrophe(), brief.quoteStyle(), brief.defaultAddress(), brief.conventions());
    }
}
