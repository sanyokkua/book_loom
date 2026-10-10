package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * Every spelling a glossary entry is known by in the book — its term and the aliases the name scan folded into it
 * ({@code Chromes} into {@code Chrome}, a misspelling into the name) — read as one name wherever the glossary is
 * matched against a text: the chunk's terms, the name checks' {@code term → target} lines and the presence test that
 * decides what a prompt lists. An alias shares its entry's target.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlossaryNames {

    private static final String ARROW = " → ";

    /**
     * The {@code name → target} lines of the entries that have a target, one per name.
     *
     * @param entries the non-null entries
     * @return the lines, each entry's term first; never null, empty when no entry has a target
     */
    public static List<String> pairs(final List<GlossaryEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        // Each caller logs the count it built; a line here would repeat it for every chunk and every check.
        return entries.stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .flatMap(entry -> entry.names().stream().map(name -> name + ARROW + entry.target()))
                .toList();
    }

    /**
     * Every name of the entries.
     *
     * @param entries the non-null entries
     * @return each entry's term, then its aliases; never null
     */
    public static List<String> terms(final List<GlossaryEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        return entries.stream().flatMap(entry -> entry.names().stream()).toList();
    }

    /**
     * Whether a text names the entry by its term or by an alias, as a prompt reads it.
     *
     * @param entry the non-null entry
     * @param text the non-null text, tokens already removed
     * @return {@code true} if one of the entry's names is named in the text, {@code false} otherwise
     */
    public static boolean isNamedIn(final GlossaryEntry entry, final String text) {
        Objects.requireNonNull(entry, "entry");
        return entry.names().stream().anyMatch(name -> TermMatch.isNamedIn(name, text));
    }
}
