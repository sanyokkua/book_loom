package ua.bookloom.pipeline.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/**
 * The fixed wording of the context builder's blocks, read from {@code blocks.properties} so no prompt text lives in
 * code. A missing key stops loading, as a mistyped template slot does.
 */
@Slf4j
final class BlockWords {

    static final String FILE = "blocks.properties";

    static final String DATA = "data";
    static final String REPLY = "reply";
    static final String RULES_HEADING = "rules.heading";
    static final String STYLE_HEADING = "style.heading";
    static final String EXAMPLES_PAIR = "examples.pair";
    static final String ANALYSIS_ROLE = "analysis.role";
    static final String ANALYSIS_DATA = "analysis.data";
    static final String ANALYSIS_REPLY = "analysis.reply";
    static final String REPAIR_LINE = "repair.line";
    static final String REPAIR_LINE_ONE = "repair.lineOne";
    static final String NOTE_HEADING = "note.heading";
    static final String MEMORY_HEADING = "memory.heading";
    static final String ITEMS_OPEN = "items.open";
    static final String ITEMS_CLOSE = "items.close";
    static final String ITEM = "item";
    static final String MAP_GLOSSARY = "map.glossary";
    static final String MAP_LOCKED = "map.locked";
    static final String MAP_SUGGESTED = "map.suggested";
    static final String MAP_SUGGESTED_NOTE = "map.suggestedNote";
    static final String MAP_LEXICON = "map.lexicon";
    static final String WINDOW_PREVIOUS = "window.previous";
    static final String WINDOW_SOURCE = "window.source";
    static final String WINDOW_TARGET = "window.target";
    static final String WINDOW_NEXT = "window.next";
    static final String WINDOW_NEXT_TARGET = "window.nextTarget";
    static final String PROOFREAD_OPEN = "proofread.open";
    static final String PROOFREAD_SOURCE = "proofread.source";
    static final String PROOFREAD_CURRENT = "proofread.current";
    static final String PROOFREAD_REASON = "proofread.reason";
    static final String PROOFREAD_CLOSE = "proofread.close";
    static final String LABEL = "label.";
    static final String EARLIER_HEADING = "earlier.heading";
    static final String EARLIER_LINE = "earlier.line";
    static final String CARD_HEADING = "card.heading";
    static final String CARD_SCENE = "card.scene";
    static final String CARD_PRESENT = "card.present";
    static final String CARD_LIKELY = "card.likely";
    static final String CARD_TYPE = "card.type.";
    static final String CARD_GENDER = "card.gender.";
    static final String CARD_NARRATOR = "card.narrator.";

    private static final List<String> REQUIRED = List.of(
            DATA,
            REPLY,
            RULES_HEADING,
            STYLE_HEADING,
            EXAMPLES_PAIR,
            ANALYSIS_ROLE,
            ANALYSIS_DATA,
            ANALYSIS_REPLY,
            REPAIR_LINE,
            REPAIR_LINE_ONE,
            NOTE_HEADING,
            MEMORY_HEADING,
            ITEMS_OPEN,
            ITEMS_CLOSE,
            ITEM,
            MAP_GLOSSARY,
            MAP_LOCKED,
            MAP_SUGGESTED,
            MAP_SUGGESTED_NOTE,
            MAP_LEXICON,
            WINDOW_PREVIOUS,
            WINDOW_SOURCE,
            WINDOW_TARGET,
            WINDOW_NEXT,
            WINDOW_NEXT_TARGET,
            PROOFREAD_OPEN,
            PROOFREAD_SOURCE,
            PROOFREAD_CURRENT,
            PROOFREAD_REASON,
            PROOFREAD_CLOSE,
            LABEL + "marks",
            LABEL + "dialogue",
            LABEL + "address",
            LABEL + "numbers",
            LABEL + "agreement",
            LABEL + "inflect",
            EARLIER_HEADING,
            EARLIER_LINE,
            CARD_HEADING,
            CARD_SCENE,
            CARD_PRESENT,
            CARD_LIKELY,
            CARD_TYPE + "CHARACTER",
            CARD_TYPE + "PLACE",
            CARD_GENDER + "FEMALE",
            CARD_GENDER + "MALE",
            CARD_GENDER + "NEUTER",
            CARD_NARRATOR + "UNKNOWN",
            CARD_NARRATOR + "MALE",
            CARD_NARRATOR + "FEMALE",
            CARD_NARRATOR + "LIKELY_MALE",
            CARD_NARRATOR + "LIKELY_FEMALE");

    private static final class Holder {
        static final BlockWords INSTANCE = load();

        private static BlockWords load() {
            final Properties properties = new Properties();
            try (InputStream stream = BlockWords.class.getResourceAsStream(FILE)) {
                if (stream == null) {
                    throw new IllegalStateException(FILE + " is missing");
                }
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            } catch (IOException cause) {
                throw new IllegalStateException(FILE + " could not be read", cause);
            }
            for (final String key : REQUIRED) {
                if (properties.getProperty(key) == null) {
                    throw new IllegalStateException(FILE + " lacks key '" + key + "'");
                }
            }
            log.debug("Loaded {} with {} keys", FILE, properties.size());
            return new BlockWords(properties);
        }
    }

    private final Properties properties;

    private BlockWords(final Properties properties) {
        this.properties = properties;
    }

    static BlockWords bundled() {
        return Holder.INSTANCE;
    }

    /**
     * The wording of a key.
     *
     * @param key one of the keys this class names
     * @return the wording; never null
     */
    String get(final String key) {
        return Objects.requireNonNull(properties.getProperty(key), key);
    }
}
