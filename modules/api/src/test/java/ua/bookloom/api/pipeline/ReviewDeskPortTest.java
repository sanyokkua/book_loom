package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Guards the review desk port against an action no screen can reach. */
class ReviewDeskPortTest {

    @Test
    void port_methods_haveNoSkipAction() {
        assertThat(Arrays.stream(ReviewDesk.class.getMethods())
                        .map(Method::getName)
                        .map(name -> name.toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.contains("skip"));
    }
}
