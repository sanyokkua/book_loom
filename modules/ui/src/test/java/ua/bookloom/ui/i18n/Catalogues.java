package ua.bookloom.ui.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reads the shipped message bundles straight from the classpath as UTF-8, so the i18n tests judge the files a user
 * gets rather than a copy of them.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Catalogues {

    private static final String RESOURCE_DIR = "/ua/bookloom/ui/i18n/";

    /**
     * Loads one catalogue.
     *
     * @param language {@code en} or {@code uk}
     * @return every key of that bundle with its pattern, sorted by key; never empty for a shipped language
     */
    static Map<String, String> load(final String language) {
        final String resource =
                RESOURCE_DIR + "messages_" + Objects.requireNonNull(language, "language") + ".properties";
        try (InputStream in = Catalogues.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("bundle missing from the classpath: " + resource);
            }
            final Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            final Map<String, String> entries = new TreeMap<>();
            properties.forEach((key, value) -> entries.put((String) key, (String) value));
            return entries;
        } catch (IOException e) {
            throw new IllegalStateException("bundle could not be read: " + resource, e);
        }
    }
}
