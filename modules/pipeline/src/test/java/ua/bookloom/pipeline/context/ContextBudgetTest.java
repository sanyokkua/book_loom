package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** How a window is divided, for a tiny, a default and a large context; the numbers are worked by hand. */
class ContextBudgetTest {

    // free = window - 900 prefix - 1100 dynamic - 500 margin; source = floor(free / (1 + 1.5)).
    @ParameterizedTest
    @CsvSource({"8192,2276,2000", "32768,12107,2000", "4096,638,638", "2048,0,0"})
    void sourceTokens_prefixDynamicAndMargin_splitWhatRemainsWithTheReply(
            final int window, final int source, final int chunk) {
        final ContextBudget budget = new ContextBudget(window, 900, 1100, 1.5);

        assertThat(budget.sourceTokens()).isEqualTo(source);
        assertThat(budget.chunkTokens()).isEqualTo(chunk);
    }

    @Test
    void outputTokens_sourceTokens_isTheRatioTimesThem() {
        assertThat(new ContextBudget(8192, 900, 1100, 1.5).outputTokens(2276)).isEqualTo(3414);
    }

    // free = window - 900 - 500; the allowance is 40% of it, never above 2000.
    @ParameterizedTest
    @CsvSource({"32768,2000", "8192,2000", "4096,1078", "2048,259", "1000,0"})
    void dynamicAllowance_window_isAShareOfWhatTheStaticPrefixLeaves(final int window, final int expected) {
        assertThat(ContextBudget.dynamicAllowance(window, 900)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"null,null,16384", "4096,null,4096", "131072,null,16384", "131072,32768,32768", "null,2048,2048"},
            nullValues = "null")
    void windowFor_detectedAndOverride_overrideWinsAndDetectionIsCapped(
            final Integer detected, final Integer override, final int expected) {
        assertThat(ContextBudget.windowFor(detected, override)).isEqualTo(expected);
    }

    @Test
    void constructor_nonPositiveWindow_isRejected() {
        assertThatThrownBy(() -> new ContextBudget(0, 0, 0, 1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describe_budget_namesEveryPartOfTheSplit() {
        assertThat(new ContextBudget(8192, 900, 1100, 1.5).describe())
                .isEqualTo(
                        "window=8192 prefix=900 dynamic=1100 margin=500 ratio=1.50 sourceTokens=2276 chunkTokens=2000");
    }
}
