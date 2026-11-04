package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.DisplayText;

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

    /**
     * Block 10; empty when the window has no previous pair, no next source and no memory line. The translation
     * memory's lines sit with the window: they only help the model keep an earlier decision. A neighbour is shown as
     * plain text: its marks belong to another item, and a mark the model is not to return is one it may copy.
     */
    static String window(final Window window, final List<String> memory, final BlockWords words) {
        final List<String> pairs = new ArrayList<>();
        for (final WindowPair pair : window.previous()) {
            pairs.add(words.get(BlockWords.WINDOW_SOURCE).replace(TEXT, DisplayText.of(pair.maskedSource())));
            final String target = pair.maskedTarget();
            if (target != null) {
                pairs.add(words.get(BlockWords.WINDOW_TARGET).replace(TEXT, DisplayText.of(target)));
            }
        }
        final List<String> sections = new ArrayList<>();
        section(sections, words.get(BlockWords.WINDOW_PREVIOUS), pairs);
        section(sections, words.get(BlockWords.MEMORY_HEADING), memory);
        final String next = window.nextSource();
        if (next != null) {
            section(sections, words.get(BlockWords.WINDOW_NEXT), List.of(DisplayText.of(next)));
        }
        return String.join("\n\n", sections);
    }

    /** Block 11b; empty when the call asks for no key term. */
    static String keyTerms(final List<String> keyTerms, final BlockWords words) {
        return keyTerms.isEmpty() ? "" : words.get(BlockWords.KEY_TERMS_HEADING) + "\n" + String.join(", ", keyTerms);
    }

    private static void section(final List<String> sections, final String heading, final List<String> lines) {
        if (!lines.isEmpty()) {
            sections.add(heading + "\n" + String.join("\n", lines));
        }
    }
}
