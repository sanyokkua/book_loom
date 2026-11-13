package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A language's name-ending table, the rows {@code nameParadigms.1}, {@code nameParadigms.2}, … of its file, so the
 * readers of name forms get it by data and never by a hard-coded tag. The rows are numbered from one without a gap.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NameParadigms {

    private static final String KEY = "nameParadigms.";

    /**
     * The rows of a language's table.
     *
     * @param languageTag the non-null language tag
     * @return the rows as written ({@code ending|gender: endings}), in their order; never null, empty when the
     *     language has no table
     */
    public static List<String> of(final String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        final LanguageRules rules = LanguageRules.bundled();
        final List<String> rows = new ArrayList<>();
        String row = rules.targetValue(languageTag, KEY + 1);
        while (row != null) {
            rows.add(row);
            row = rules.targetValue(languageTag, KEY + (rows.size() + 1));
        }
        return List.copyOf(rows);
    }
}
