package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ReviewMode;

class ReviewModeResolverTest {

    // The environment wins over the property, a name is trimmed and read in any case, and a value that names no mode
    // falls back to Unattended while the resolver keeps the rejected text for the launcher to report.
    @ParameterizedTest
    @CsvSource(
            nullValues = "NIL",
            value = {
                "Semi-Manual, NIL, ASSISTED, ENVIRONMENT, NIL",
                "auto, manual, UNATTENDED, ENVIRONMENT, NIL",
                "NIL, MANUAL, MANUAL, PROPERTY, NIL",
                "NIL, '  assisted ', ASSISTED, PROPERTY, NIL",
                "unattended, NIL, UNATTENDED, ENVIRONMENT, NIL",
                "strict, manual, UNATTENDED, DEFAULT, strict",
                "NIL, loud, UNATTENDED, DEFAULT, loud",
                "NIL, NIL, UNATTENDED, DEFAULT, NIL"
            })
    void resolve_environmentAndProperty_chooseTheModeSourceAndRejection(
            @Nullable String env,
            @Nullable String property,
            ReviewMode expectedMode,
            ResolvedReviewMode.Source expectedSource,
            @Nullable String expectedRejected) {
        final ResolvedReviewMode resolved = ReviewModeResolver.resolve(
                lookup("BOOKLOOM_REVIEW_MODE", env), lookup("bookloom.review.mode", property));

        assertThat(resolved.mode()).isEqualTo(expectedMode);
        assertThat(resolved.source()).isEqualTo(expectedSource);
        assertThat(resolved.rejectedValue()).isEqualTo(expectedRejected);
    }

    private static Function<String, @Nullable String> lookup(String name, @Nullable String value) {
        final Map<String, String> values = new HashMap<>();
        values.put(name, value);
        return values::get;
    }
}
