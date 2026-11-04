package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Writes blocks 9 and 10 of a translate-family prompt: the map lines the window names, and the previous pairs with the
 * next source. Each section is a heading and its lines, and a blank line ends it, so a section never runs into the
 * next.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowBlocks {

    private static final String TEXT = "{text}";

    /** Block 9; empty when the window names nothing on the map. */
    static String map(final MapLines lines, final BlockWords words) {
        final List<String> sections = new ArrayList<>();
        section(sections, words.get(BlockWords.MAP_GLOSSARY), lines.glossary());
        section(sections, words.get(BlockWords.MAP_LOCKED), lines.locked());
        if (!lines.suggested().isEmpty()) {
            final List<String> body = new ArrayList<>();
            body.add(words.get(BlockWords.MAP_SUGGESTED_NOTE));
            body.addAll(lines.suggested());
            section(sections, words.get(BlockWords.MAP_SUGGESTED), body);
        }
        section(sections, words.get(BlockWords.MAP_LEXICON), lines.lexicon());
        return String.join("\n\n", sections);
    }

    /** Block 10; empty when the window has no previous pair and no next source. */
    static String window(final Window window, final BlockWords words) {
        final List<String> pairs = new ArrayList<>();
        for (final WindowPair pair : window.previous()) {
            pairs.add(words.get(BlockWords.WINDOW_SOURCE).replace(TEXT, pair.maskedSource()));
            final String target = pair.maskedTarget();
            if (target != null) {
                pairs.add(words.get(BlockWords.WINDOW_TARGET).replace(TEXT, target));
            }
        }
        final List<String> sections = new ArrayList<>();
        section(sections, words.get(BlockWords.WINDOW_PREVIOUS), pairs);
        final String next = window.nextSource();
        if (next != null) {
            section(sections, words.get(BlockWords.WINDOW_NEXT), List.of(next));
        }
        return String.join("\n\n", sections);
    }

    private static void section(final List<String> sections, final String heading, final List<String> lines) {
        if (!lines.isEmpty()) {
            sections.add(heading + "\n" + String.join("\n", lines));
        }
    }
}
