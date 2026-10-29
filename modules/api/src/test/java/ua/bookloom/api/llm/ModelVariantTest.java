package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A typed LM Studio id loses only a real {@code @variant} suffix. */
class ModelVariantTest {

    @ParameterizedTest
    @CsvSource(
            nullValues = "NONE",
            value = {
                "google/gemma-4-e4b@q4_k_m, google/gemma-4-e4b, q4_k_m",
                "google/gemma-4-26b-a4b-qat@4bit, google/gemma-4-26b-a4b-qat, 4bit",
                "google/gemma-4-e4b, google/gemma-4-e4b, NONE",
                "@q4, @q4, NONE",
                "model@, model@, NONE"
            })
    void of_typedId_splitsTheVariant(final String typed, final String id, final String variant) {
        assertThat(ModelVariant.of(typed)).isEqualTo(new ModelVariant(id, variant));
    }
}
