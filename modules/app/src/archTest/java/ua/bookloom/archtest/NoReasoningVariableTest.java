package ua.bookloom.archtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards that thinking is decided per model from its profile and the person's choice on Settings › Models (design
 * D20): no production class names the retired {@code BOOKLOOM_REASONING} variable or its {@code bookloom.reasoning}
 * property, so neither can be read again.
 */
class NoReasoningVariableTest {

    private static final List<String> RETIRED_NAMES = List.of("BOOKLOOM_REASONING", "bookloom.reasoning");

    @Test
    void productionClasses_neverNameTheReasoningVariable() {
        assertThat(ProductionClasses.naming(RETIRED_NAMES))
                .as("production classes that name BOOKLOOM_REASONING")
                .isEmpty();
    }
}
