package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** What counts as the same thing being tested: a name does not, an address does. */
class TestSubjectTest {

    private static ProviderConfig config(final String name, final String address) {
        return new ProviderConfig(
                "office-lm",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(address),
                Duration.ofSeconds(10),
                Duration.ofSeconds(180),
                name,
                ProviderAuth.NONE,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of());
    }

    private static TestSubject subject(final String name, final String address, final String model) {
        return new TestSubject(config(name, address), model, name, true);
    }

    @Test
    void key_nameEdited_isUnchanged() {
        final TestSubject before = subject("Office LM", "http://10.0.0.5:1234/v1", "qwen");

        final TestSubject after = subject("Office LM 2", "http://10.0.0.5:1234/v1", "qwen");

        assertThat(after.key()).isEqualTo(before.key());
    }

    @Test
    void key_addressEdited_differs() {
        final TestSubject before = subject("Office LM", "http://10.0.0.5:1234/v1", "qwen");

        final TestSubject after = subject("Office LM", "http://10.0.0.6:1234/v1", "qwen");

        assertThat(after.key()).isNotEqualTo(before.key());
    }

    @Test
    void key_modelPaddedWithSpaces_isUnchanged() {
        final TestSubject before = subject("Office LM", "http://10.0.0.5:1234/v1", "qwen");

        final TestSubject after = subject("Office LM", "http://10.0.0.5:1234/v1", "  qwen ");

        assertThat(after.key()).isEqualTo(before.key());
    }

    @Test
    void key_storedFlagDiffers_differs() {
        final TestSubject saved = subject("Office LM", "http://10.0.0.5:1234/v1", "qwen");
        final TestSubject unsaved = new TestSubject(saved.config(), "qwen", "Office LM", false);

        assertThat(unsaved.key()).isNotEqualTo(saved.key());
    }
}
