package ua.bookloom.pipeline.prompt;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * One bundled language or pair file: its keys and values. A missing file is an empty one, so a language the map does
 * not know reads the same as one that states nothing.
 *
 * @param name the file's base name, such as {@code uk} or {@code en-uk}
 * @param values the file's keys, never changed after loading
 */
record LanguageFile(String name, Map<String, String> values) {

    /** Copies the values. */
    LanguageFile {
        Objects.requireNonNull(name, "name");
        values = Map.copyOf(values);
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
