package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The stages of the names pass: the one list the engine announces and the progress dialog names. */
class NamesPassStageTest {

    // IF the order differed from the buttons' order, THEN the dialog would list steps in a sequence the pass never
    // runs.
    @Test
    void codes_inOrder_areTheEightPassStages() {
        assertThat(Arrays.stream(NamesPassStage.values()).map(NamesPassStage::code))
                .containsExactly(
                        "scan",
                        "names",
                        "terms-scan",
                        "terms-review",
                        "terms-read",
                        "names-translate",
                        "terms-translate",
                        "write");
    }
}
