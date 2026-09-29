package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.document.DocumentModule;

/**
 * The context size reaches an Ollama server as num_ctx and never reaches an OpenAI-compatible one; the output cap
 * reaches each in its own dialect.
 */
class ContextWindowWireMockTest {

    @TempDir
    private Path tempDir;

    @Test
    void translate_draftToOllama_postsNumCtxInsideOptions() {
        assertThat(draftBody(ProviderKind.OLLAMA)).contains("\"options\":{").contains("\"num_ctx\":8192");
    }

    @Test
    void translate_draftToOpenAiCompatible_sendsNoContextSize() {
        assertThat(draftBody(ProviderKind.OPENAI_COMPATIBLE))
                .doesNotContain("num_ctx", "n_ctx", "context_length", "\"options\"");
    }

    // A two-placeholder short source hits the 64-token floor of the cap.
    @Test
    void translate_draftToOllama_postsNumPredictInsideOptions() {
        assertThat(draftBody(ProviderKind.OLLAMA))
                .contains("\"options\":{")
                .contains("\"num_predict\":64")
                .doesNotContain("max_tokens");
    }

    @Test
    void translate_draftToOpenAiCompatible_postsMaxTokens() {
        assertThat(draftBody(ProviderKind.OPENAI_COMPATIBLE))
                .contains("\"max_tokens\":64")
                .doesNotContain("num_predict", "\"options\"");
    }

    private String draftBody(final ProviderKind kind) {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Segment segment = Objects.requireNonNull(documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door."))
                        .data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.target("Він відчинив ⟦g0⟧старі⟦g1⟧ двері.", Duration.ZERO));
            final SegmentTranslator translator = TranslationJobTestSupport.segmentTranslator(
                    documents, provider.model(Duration.ofSeconds(5), duration -> {}), BookFormat.MARKDOWN, "uk", "en");
            translator.translate(segment);
            return provider.chatBodies().getFirst();
        }
    }
}
