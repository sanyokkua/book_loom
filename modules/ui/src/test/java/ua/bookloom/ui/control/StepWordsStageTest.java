package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.ui.state.StepEntry;

/**
 * A stage that finished without a request (finding candidates, checking, writing the results) did its work, so its
 * marker is the done tick and its line says "no request"; a single step that ended with no call keeps the dash.
 */
class StepWordsStageTest {

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "STAGE, NO_CALL, DONE",
        "STAGE, DONE, DONE",
        "STAGE, FAILED, FAILED",
        "REQUEST, NO_CALL, NO_CALL",
        "REQUEST, DONE, DONE"
    })
    void look_endedStep_readsByItsKindAndResult(
            final StepKind kind, final StepResult result, final StepWords.Look expected) {
        final StepEntry step = new StepEntry(kind, "write", 0, StepState.ENDED, null, result, null);

        assertThat(StepWords.Look.of(step)).isEqualTo(expected);
    }
}
