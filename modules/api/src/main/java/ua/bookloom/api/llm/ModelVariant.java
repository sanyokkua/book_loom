package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A typed LM Studio model id split from its {@code @variant} suffix. LM Studio loads a second copy of a model for an id
 * that names a variant ({@code google/gemma-4-e4b@q4_k_m}), which can exhaust memory, so only the plain id is stored
 * or sent; the removed suffix is kept so the removal can be said once.
 *
 * @param id the id without its variant; never blank when the typed id was not
 * @param variant the removed suffix without its {@code @}, or null when the typed id named none
 */
public record ModelVariant(String id, @Nullable String variant) {

    private static final char VARIANT_MARK = '@';

    /** Rejects a missing id. */
    public ModelVariant {
        Objects.requireNonNull(id, "id");
    }

    /**
     * Splits a typed id at its last {@code @}; an {@code @} at the start or the end splits nothing.
     *
     * @param typed the id as the person typed it; non-null
     * @return the id and its removed variant, or the typed id with no variant when it names none
     */
    public static ModelVariant of(final String typed) {
        Objects.requireNonNull(typed, "typed");
        final int mark = typed.lastIndexOf(VARIANT_MARK);
        if (mark <= 0 || mark == typed.length() - 1) {
            return new ModelVariant(typed, null);
        }
        return new ModelVariant(typed.substring(0, mark), typed.substring(mark + 1));
    }

    /**
     * Whether a variant was removed.
     *
     * @return {@code true} if the typed id named a variant, {@code false} otherwise
     */
    public boolean hasVariant() {
        return variant != null;
    }
}
