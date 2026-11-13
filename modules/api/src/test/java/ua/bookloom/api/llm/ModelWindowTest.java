package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The one window every call to a model is sized from: the person's, else the smallest of what is known and 16,384. */
class ModelWindowTest {

    private static final int OUTPUT_MAX = 8192;

    @ParameterizedTest
    @CsvSource({
        // override, detected, loaded, hard cap, expected
        "8192, 32768, , 16384, 8192",
        ", 32768, , 16384, 16384",
        ", 4096, , 16384, 4096",
        ", 131072, , 32768, 16384",
        ", , , 16384, 16384",
        ", 131072, 8192, 32768, 8192",
        "65536, 131072, 8192, 32768, 65536",
        ", , , 4096, 4096"
    })
    void of_overrideDetectedAndCap_returnsTheExpected(
            final @Nullable Integer override,
            final @Nullable Integer detected,
            final @Nullable Integer loaded,
            final int hardCap,
            final int expected) {
        final ContextPolicy policy = new ContextPolicy(hardCap, OUTPUT_MAX, detected, loaded);

        assertThat(ModelWindow.of(policy, override)).isEqualTo(expected);
    }

    // IF a hosted model were held to 16,384 like a local one, THEN a 200k window the provider offers at no memory cost
    // would be wasted and a thinking model would have no room; the model's own window stands, and what is not known
    // still falls back to the default.
    @ParameterizedTest
    @CsvSource({
        // override, detected, loaded, hard cap, expected
        ", 200000, , 16384, 200000",
        ", 1000000, , 16384, 1000000",
        ", 200000, 32768, 16384, 32768",
        ", , , 16384, 16384",
        "65536, 200000, , 16384, 65536"
    })
    void of_hostedModel_isNotHeldToTheDefaultWindow(
            final @Nullable Integer override,
            final @Nullable Integer detected,
            final @Nullable Integer loaded,
            final int hardCap,
            final int expected) {
        final ContextPolicy policy = new ContextPolicy(hardCap, OUTPUT_MAX, detected, loaded, true);

        assertThat(ModelWindow.of(policy, override)).isEqualTo(expected);
    }
}
