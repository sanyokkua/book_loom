package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.CallKind;

/** The draft step's three calls, each one call kind. */
class DraftStepTest {

    @ParameterizedTest
    @CsvSource({"DRAFT,DRAFT", "STRUCTURAL_REPAIR,STRUCTURAL_REPAIR", "REPAIR,REPAIR"})
    void callKind_eachStep_isTheMatchingKind(final DraftStep step, final CallKind expected) {
        assertThat(step.callKind()).isEqualTo(expected);
    }
}
