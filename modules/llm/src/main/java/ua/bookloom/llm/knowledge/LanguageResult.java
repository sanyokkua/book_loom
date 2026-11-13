package ua.bookloom.llm.knowledge;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.LanguageState;

/**
 * The answer for one model and one language tag.
 *
 * @param state what the maker's list lets the program conclude
 * @param source where that came from
 * @param rowKey the key of the table row used, or null when none was
 */
public record LanguageResult(
        LanguageState state,
        LanguageSource source,
        @Nullable String rowKey) {

    public LanguageResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(source, "source");
    }
}
