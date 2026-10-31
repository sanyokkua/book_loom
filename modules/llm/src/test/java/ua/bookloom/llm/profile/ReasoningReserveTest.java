package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.SourceSize;

/** The room a thinking call gets for its reasoning: max(2048, 4·S + 35·n) for a source of S tokens in n items. */
class ReasoningReserveTest {

    @ParameterizedTest
    @CsvSource({"300,4,2048", "800,4,3340", "0,0,2048", "1000,8,4280"})
    void tokens_sourceSize_isTheFloorOrFourPerTokenAndThirtyFivePerItem(
            final int sourceTokens, final int items, final int reserve) {
        assertThat(ReasoningReserve.tokens(new SourceSize(sourceTokens, items))).isEqualTo(reserve);
    }

    @Test
    void tokens_noSourceStated_isTheFloor() {
        assertThat(ReasoningReserve.tokens(null)).isEqualTo(2048);
    }
}
