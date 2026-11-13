package ua.bookloom.archtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards that sampling is decided per model from its profile and the person's choice on Settings › Models (design
 * D20): no production class names the retired {@code BOOKLOOM_SAMPLING} variable or its {@code bookloom.sampling}
 * property, so neither can be read again.
 */
class NoSamplingVariableTest {

    private static final List<String> RETIRED_NAMES = List.of("BOOKLOOM_SAMPLING", "bookloom.sampling");

    @Test
    void productionClasses_neverNameTheSamplingVariable() {
        assertThat(ProductionClasses.naming(RETIRED_NAMES))
                .as("production classes that name BOOKLOOM_SAMPLING")
                .isEmpty();
    }
}
