package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.ModelReport;

/**
 * What the port said about one model for one language pair: the report, the language advice and the date of the shipped
 * knowledge. A reading is a snapshot; it is replaced, never changed.
 *
 * @param modelId the model the reading is about
 * @param report the model's report, with its notes
 * @param languages what the maker's list says about the pair
 * @param factsDate the date of the shipped model knowledge, or an empty text
 * @param sourceTag the source language the advice was asked for, or null for none
 * @param targetTag the target language the advice was asked for, or null for none
 */
public record AdviceReading(
        String modelId,
        ModelReport report,
        LanguageAdvice languages,
        String factsDate,
        @Nullable String sourceTag,
        @Nullable String targetTag) {

    public AdviceReading {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(languages, "languages");
        Objects.requireNonNull(factsDate, "factsDate");
    }
}
