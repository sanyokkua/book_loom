package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The id a new provider gets from its name. */
class ProviderIdsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Office LM|office-lm",
                "  My  Server!! |my-server",
                "LM Studio #2|lm-studio-2",
                "Мій сервер|provider",
                "'   '|provider"
            })
    void newId_name_isALowerCaseSlug(final String name, final String expected) {
        assertThat(ProviderIds.newId(name, Set.of())).isEqualTo(expected);
    }

    @Test
    void newId_slugTaken_appendsTheNextFreeNumber() {
        assertThat(ProviderIds.newId("Office LM", Set.of("office-lm", "office-lm-2")))
                .isEqualTo("office-lm-3");
    }

    @Test
    void newId_fallbackTaken_numbersTheFallback() {
        assertThat(ProviderIds.newId("", Set.of("provider"))).isEqualTo("provider-2");
    }
}
