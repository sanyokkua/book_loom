package ua.bookloom.pipeline.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;

/**
 * The English phrase the style sheet uses for every brief choice, read from {@code style-phrases.properties}. A missing
 * key stops loading, exactly as a mistyped template slot does, so no brief choice can silently go unstated.
 */
final class StylePhrases {

    static final String FILE = "style-phrases.properties";
    static final int BANDS = 5;
    private static final int BAND_WIDTH = 20;

    private static final class Holder {
        static final StylePhrases INSTANCE = load(() -> StylePhrases.class.getResourceAsStream(FILE));
    }

    private final Properties properties;

    private StylePhrases(final Properties properties) {
        this.properties = properties;
    }

    static StylePhrases bundled() {
        return Holder.INSTANCE;
    }

    static StylePhrases load(final Supplier<@Nullable InputStream> source) {
        final Properties properties = new Properties();
        try (InputStream stream = source.get()) {
            if (stream == null) {
                throw new IllegalStateException(FILE + " is missing");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException cause) {
            throw new IllegalStateException(FILE + " could not be read", cause);
        }
        requireEveryKey(properties);
        return new StylePhrases(properties);
    }

    String defaultLine() {
        return properties.getProperty("default.line");
    }

    String balance(final int band) {
        return properties.getProperty("balance.band" + band);
    }

    static int bandOf(final int balance) {
        return Math.min(BANDS, balance / BAND_WIDTH + 1);
    }

    String phrase(final Enum<?> choice) {
        return properties.getProperty(key(choice));
    }

    private static String key(final Enum<?> choice) {
        return choice.getDeclaringClass().getSimpleName() + "." + choice.name();
    }

    private static void requireEveryKey(final Properties properties) {
        final Stream<String> keys = Stream.of(
                        Register.values(),
                        NamePolicy.values(),
                        ForeignPassagePolicy.values(),
                        FootnotePolicy.values(),
                        UnitPolicy.values())
                .flatMap(Arrays::stream)
                .map(choice -> key((Enum<?>) choice));
        final Stream<String> bands =
                Stream.iterate(1, band -> band + 1).limit(BANDS).map(band -> "balance.band" + band);
        Stream.concat(Stream.concat(keys, bands), Stream.of("default.line")).forEach(key -> {
            if (properties.getProperty(Objects.requireNonNull(key)) == null) {
                throw new IllegalStateException(FILE + " lacks key '" + key + "'");
            }
        });
    }
}
