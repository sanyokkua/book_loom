package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** Which tested providers are Ollama or LM Studio on this computer, whose silence means the app is closed. */
class TestSubjectLocalTest {

    // IF a remote server or an unknown local one were called a closed app, THEN the advice to open it would be wrong.
    @ParameterizedTest
    @CsvSource({
        "ollama, OLLAMA, http://127.0.0.1:11434, Ollama, true",
        "ollama, OLLAMA, http://localhost:11434, Ollama, true",
        "lmstudio, OPENAI_COMPATIBLE, http://127.0.0.1:1234/v1, LM Studio, true",
        "ollama, OLLAMA, http://10.0.0.5:11434, Ollama, false",
        "office, OPENAI_COMPATIBLE, http://127.0.0.1:8000/v1, Office, false",
        "lmstudio, OPENAI_COMPATIBLE, https://models.example.com/v1, LM Studio, false"
    })
    void isLocalProgram_providerAndAddress_isTrueOnlyForOllamaOrLmStudioOnThisComputer(
            final String id, final ProviderKind kind, final String address, final String name, final boolean local) {
        final ProviderConfig config =
                new ProviderConfig(id, kind, URI.create(address), Duration.ofSeconds(5), Duration.ofSeconds(60));

        assertThat(new TestSubject(config, "gemma4:e4b", name, true).isLocalProgram())
                .isEqualTo(local);
    }
}
