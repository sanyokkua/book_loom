package ua.bookloom.pipeline.prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reads the rows of a language file's {@code translit} key. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslitRows {

    /**
     * The letter table the rows write, each row {@code source>letters} ({@code sh>ш}).
     *
     * @param rows the file's whitespace-separated rows; a row without a source or without letters is skipped
     * @return the table by lower-case source; never null, empty when there is no usable row
     */
    static Map<String, List<String>> table(final List<String> rows) {
        final Map<String, List<String>> table = new HashMap<>();
        for (final String row : rows) {
            final int arrow = row.indexOf('>');
            if (arrow > 0 && arrow < row.length() - 1) {
                final List<String> letters = List.of(row.substring(arrow + 1).split(""));
                table.put(row.substring(0, arrow).toLowerCase(Locale.ROOT), letters);
            }
        }
        return Map.copyOf(table);
    }
}
