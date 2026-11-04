package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The words the Models tab and Start need about one model that its identity alone does not give.
 *
 * @param label the person-facing name with its size, such as {@code Hermes 4 70B}; non-blank
 * @param familyLabel the name of the family and generation without a size, such as {@code Llama 3.1}; may be empty
 *     when the name gave no family
 * @param libraryTag the library model that has the thinking switch this download lacks, such as {@code qwen3.5:4b},
 *     or null when the download needs no such hint
 * @param serverQuant the quant label the server reports, or null when the server was not asked or said nothing
 * @param quantMeasured whether a built-in row names a BookLoom measurement of this model at this quant
 */
public record ModelNotes(
        String label,
        String familyLabel,
        @Nullable String libraryTag,
        @Nullable String serverQuant,
        boolean quantMeasured) {

    public ModelNotes {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(familyLabel, "familyLabel");
    }
}
