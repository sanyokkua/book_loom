package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.chunk.TokenBudget;

/**
 * Guards that the window has one owner, {@code ModelWindow} in {@code :api} (design D20): the pipeline's own window
 * rule and its fixed 16,384 constants are gone, so no call can be sized by a second rule again.
 */
class WindowOwnerTest {

    @Test
    void pipelineWindowRules_areGone() {
        assertThat(Arrays.stream(ContextBudget.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("windowFor");
        assertThat(Arrays.stream(ContextBudget.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("DEFAULT_WINDOW");
        assertThat(Arrays.stream(TokenBudget.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("EFFECTIVE_CONTEXT");
    }
}
