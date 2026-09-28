package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The per-chunk token budget and the output allowance a full chunk reserves. */
class TokenBudgetTest {

    @ParameterizedTest
    @CsvSource({"0,1200", "6992,1200", "7500,692"})
    void chunkTokens_headroom_capsAtTwelveHundredWithinTheContext(final int headroom, final int expected) {
        assertThat(TokenBudget.chunkTokens(headroom)).isEqualTo(expected);
    }

    @Test
    void fullChunkAllowance_englishToUkrainian_isTwoThousandEightHundredEighty() {
        assertThat(TokenBudget.fullChunkAllowance("en", "uk")).isEqualTo(2880);
    }
}
