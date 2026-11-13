package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the maker's language list lets the program say about a model for one language pair. A side the caller did not
 * name counts as {@link LanguageState#DECLARED}: nothing is claimed about it.
 *
 * @param sourceState the state of the source language
 * @param targetState the state of the target language
 * @param baseOnly whether a language counted as declared is declared by the base model of a fine-tune only
 * @param pairTested whether a BookLoom run of this model on exactly this pair is on record
 * @param familyLabel the model's family name for a sentence, such as {@code Llama 3.1}; may be empty
 * @param baseLabel the base model's name when the declaration is its, or null
 * @param baseCodes the languages the declaration lists, {@code uk} and {@code ru} first, then the rest alphabetically;
 *     empty unless {@code baseLabel} is set
 */
public record LanguageAdvice(
        LanguageState sourceState,
        LanguageState targetState,
        boolean baseOnly,
        boolean pairTested,
        String familyLabel,
        @Nullable String baseLabel,
        List<String> baseCodes) {

    /** The advice of a port that reads no language tables: nothing to say. */
    public static final LanguageAdvice NEUTRAL =
            new LanguageAdvice(LanguageState.DECLARED, LanguageState.DECLARED, false, false, "", null, List.of());

    public LanguageAdvice {
        Objects.requireNonNull(sourceState, "sourceState");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(familyLabel, "familyLabel");
        baseCodes = List.copyOf(Objects.requireNonNull(baseCodes, "baseCodes"));
    }
}
