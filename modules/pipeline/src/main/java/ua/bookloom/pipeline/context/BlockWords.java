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

    static final String ROLE = "role";
    static final String DATA = "data";
    static final String REPLY = "reply";
    static final String RULES_HEADING = "rules.heading";
    static final String STYLE_HEADING = "style.heading";
    static final String EXAMPLES_PAIR = "examples.pair";
    static final String ANALYSIS_ROLE = "analysis.role";
    static final String ANALYSIS_DATA = "analysis.data";
    static final String ANALYSIS_REPLY = "analysis.reply";
    static final String REPAIR_HEADING = "repair.heading";
    static final String REPAIR_LINE = "repair.line";
    static final String TASK_TRANSLATE = "task.translate";
    static final String ITEMS_OPEN = "items.open";
    static final String ITEMS_CLOSE = "items.close";
    static final String ITEM = "item";
    static final String LABEL = "label.";

    private static final List<String> REQUIRED = List.of(
            ROLE,
            DATA,
            REPLY,
            RULES_HEADING,
            STYLE_HEADING,
            EXAMPLES_PAIR,
            ANALYSIS_ROLE,
            ANALYSIS_DATA,
            ANALYSIS_REPLY,
            REPAIR_HEADING,
            REPAIR_LINE,
            TASK_TRANSLATE,
            ITEMS_OPEN,
            ITEMS_CLOSE,
            ITEM,
            LABEL + "marks",
            LABEL + "dialogue",
            LABEL + "address",
            LABEL + "numbers",
            LABEL + "agreement",
            LABEL + "inflect");

    private static final class Holder {
        static final BlockWords INSTANCE = load();
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
