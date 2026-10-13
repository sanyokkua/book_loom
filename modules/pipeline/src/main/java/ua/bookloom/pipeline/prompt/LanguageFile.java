package ua.bookloom.pipeline.prompt;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * One bundled language or pair file: its keys and values. A missing file is an empty one, so a language the map does
 * not know reads the same as one that states nothing.
 *
 * @param name the file's base name, such as {@code uk} or {@code en-uk}
 * @param values the file's keys, never changed after loading
 */
@Slf4j
record LanguageFile(String name, Map<String, String> values) {

    /** Copies the values. */
    LanguageFile {
        Objects.requireNonNull(name, "name");
        values = Map.copyOf(values);
    }

    /** Reads a bundled file; a resource the loader does not have is an empty file. */
    static LanguageFile load(final String name, final String resource, final PromptTemplates.ResourceLoader loader) {
        final Properties properties = new Properties();
        try (var stream = loader.open(resource)) {
            if (stream == null) {
                log.debug("No language file {}", resource);
                return new LanguageFile(name, Map.of());
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException cause) {
            throw new IllegalStateException(resource + " could not be read", cause);
        }
        final Map<String, String> values = new HashMap<>();
        properties.forEach(
                (key, value) -> values.put(key.toString(), value.toString().strip()));
        log.debug("Loaded language file {} with {} keys", resource, values.size());
        return new LanguageFile(name, values);
    }

    boolean exists() {
        return !values.isEmpty();
    }

    @Nullable
    String get(final String key) {
        return values.get(key);
    }

    /** The values of {@code prefix.1}, {@code prefix.2}, … in numeric order; keys with a further suffix are skipped. */
    List<String> indexed(final String prefix) {
        final Map<Integer, String> found = new TreeMap<>();
        final String start = prefix + ".";
        values.forEach((key, value) -> {
            final String index = key.startsWith(start) ? key.substring(start.length()) : "";
            if (!index.isEmpty() && index.chars().allMatch(Character::isDigit)) {
                found.put(Integer.parseInt(index), value);
            }
        });
        return List.copyOf(found.values());
    }
}
