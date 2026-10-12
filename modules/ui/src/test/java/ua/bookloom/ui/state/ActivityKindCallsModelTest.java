package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Which kinds of work send the model a request, and so keep the computer awake: every kind except the four that never
 * do. A new kind is a model caller until it is listed here as one that is not, which makes that a decision.
 */
class ActivityKindCallsModelTest {

    // IF a kind that asks the model did not hold the machine awake, THEN a names pass or an export left running would
    // be
    // frozen by an idle sleep, as the owner's overnight runs showed.
    @ParameterizedTest
    @EnumSource(
            value = ActivityKind.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"PROVIDER_CHECK", "MODEL_LISTING", "IMPORT"})
    void isCallingModel_kindThatAsksTheModel_isTrue(final ActivityKind kind) {
        assertThat(kind.isCallingModel()).isTrue();
    }

    // The light provider checks ask the server, not the model, and opening a book reads a file: neither is a reason to
    // hold the computer awake.
    @ParameterizedTest
    @EnumSource(
            value = ActivityKind.class,
            mode = EnumSource.Mode.INCLUDE,
            names = {"PROVIDER_CHECK", "MODEL_LISTING", "IMPORT"})
    void isCallingModel_kindThatNeverAsksTheModel_isFalse(final ActivityKind kind) {
        assertThat(kind.isCallingModel()).isFalse();
    }
}
