package ua.bookloom.llm.client.ollama;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;

/**
 * The two top-level fields of an Ollama-native body that go with the model's window: {@code truncate:false} beside the
 * window, so a prompt longer than it is refused instead of cut from the front, and {@code keep_alive} on a translation
 * run's own calls, so a pause for review does not unload the model. The "Ollama chat started" line logs both inputs.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OllamaWindow {

    /**
     * How long Ollama keeps the model loaded after a run's call: long enough for a proofread window or a pause for
     * review, after which Ollama's own idle rule applies again.
     */
    static final String KEEP_ALIVE = "30m";

    static @Nullable String keepAlive(final ChatRequest request) {
        return Objects.requireNonNull(request, "request").keepLoaded() ? KEEP_ALIVE : null;
    }

    static @Nullable Boolean truncate(final ChatRequest request) {
        return Objects.requireNonNull(request, "request").contextWindow() == null ? null : Boolean.FALSE;
    }
}
