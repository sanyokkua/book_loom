package ua.bookloom.llm.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Both bundled tables, parsed and immutable.
 *
 * @param dataVersion the {@code data-version} both files carry
 * @param quantRows the quant rows keyed by upper-case base type
 * @param languageRows the language rows in file order
 */
public record KnowledgeTables(String dataVersion, Map<String, QuantRow> quantRows, List<LanguageRow> languageRows) {

    public KnowledgeTables {
        Objects.requireNonNull(dataVersion, "dataVersion");
        quantRows = Map.copyOf(quantRows);
        languageRows = List.copyOf(languageRows);
    }

    /**
     * Finds a quant row.
     *
     * @param token a base type, any letter case
     * @return the row, or empty when the table has none
     */
    public Optional<QuantRow> quantRow(String token) {
        return Optional.ofNullable(quantRows.get(token.toUpperCase(java.util.Locale.ROOT)));
    }

    /**
     * Finds a language row by its key.
     *
     * @param key the row key
     * @return the row, or empty when the table has none
     */
    public Optional<LanguageRow> languageRow(String key) {
        return languageRows.stream().filter(row -> row.key().equals(key)).findFirst();
    }
}
