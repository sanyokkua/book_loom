package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RunRequestTest {

    @Test
    void new_withoutADetectedContext_leavesItAbsent() {
        assertThat(new RunRequest("p1", ReviewMode.ASSISTED).detectedContext()).isNull();
    }

    @Test
    void new_withADetectedContext_keepsIt() {
        assertThat(new RunRequest("p1", ReviewMode.ASSISTED, 4096).detectedContext())
                .isEqualTo(4096);
    }

    @Test
    void new_nonPositiveDetectedContext_isRejected() {
        assertThatThrownBy(() -> new RunRequest("p1", ReviewMode.ASSISTED, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
