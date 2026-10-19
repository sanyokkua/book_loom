package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The draft step's three calls, each one prompt of the catalogue. */
class DraftStepTest {

    @ParameterizedTest
    @CsvSource({"DRAFT,DRAFT", "STRUCTURAL_REPAIR,STRUCTURAL_REPAIR", "PLACEHOLDER_REPAIR,PLACEHOLDER_REPAIR"})
    void promptName_eachStep_isTheMatchingPrompt(final DraftStep step, final PromptName expected) {
        assertThat(step.promptName()).isEqualTo(expected);
    }
}
