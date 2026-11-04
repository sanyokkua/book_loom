package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;

/**
 * The map lines one call carries in block 9 (design D8), each list in the order the window first names its entries.
 *
 * @param glossary the person's and the code's glossary lines, {@code base form → target (type, gender)}; copied
 * @param locked the locked names the window hides behind tokens, {@code ⟦gN⟧ → target, type, gender}; copied
 * @param suggested the lines of entries whose target the model suggested and nobody confirmed; copied
 * @param lexicon the recurring terms' established renderings, {@code term → rendering}; copied
 */
public record MapLines(List<String> glossary, List<String> locked, List<String> suggested, List<String> lexicon) {

    /** Lines for a window that names nothing on the map. */
    public static final MapLines NONE = new MapLines(List.of(), List.of(), List.of(), List.of());

    /** Copies the lists. */
    public MapLines {
        glossary = List.copyOf(Objects.requireNonNull(glossary, "glossary"));
        locked = List.copyOf(Objects.requireNonNull(locked, "locked"));
        suggested = List.copyOf(Objects.requireNonNull(suggested, "suggested"));
        lexicon = List.copyOf(Objects.requireNonNull(lexicon, "lexicon"));
    }

    /**
     * The lines of the given entries, each section in the entries' order.
     *
     * @param entries the non-null entries
     * @return the lines; {@link #NONE}-equal for no entry
     */
    public static MapLines of(final List<MapEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        return new MapLines(
                linesOf(entries, MapEntry.Section.GLOSSARY),
                linesOf(entries, MapEntry.Section.LOCKED),
                linesOf(entries, MapEntry.Section.SUGGESTED),
                linesOf(entries, MapEntry.Section.LEXICON));
    }

    private static List<String> linesOf(final List<MapEntry> entries, final MapEntry.Section section) {
        return entries.stream()
                .filter(entry -> entry.section() == section)
                .map(MapEntry::line)
                .toList();
    }

    /** {@return whether the window names nothing on the map, so block 9 is left out} */
    public boolean isEmpty() {
        return glossary.isEmpty() && locked.isEmpty() && suggested.isEmpty() && lexicon.isEmpty();
    }
}
