package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ContextPolicy;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelProfile;

/** The window a run sizes for is latched when a chunk starts, so a save mid-chunk changes the next chunk only. */
class RunWindowTest {

    private static final int HARD_CAP = 32_768;
    private static final int OUTPUT_MAX = 8_192;

    /** A model whose server reported a window, and whose person's window can be saved while the run goes. */
    private static final class Windowed implements ChatModel {

        private final AtomicReference<@Nullable Integer> override = new AtomicReference<>();
        private final @Nullable Integer detected;
        private final @Nullable Integer loaded;

        Windowed(final @Nullable Integer detected, final @Nullable Integer loaded) {
            this.detected = detected;
            this.loaded = loaded;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            return Result.ok(new ChatResponse("ok", FinishReason.STOP));
        }

        @Override
        public @Nullable Integer contextWindowOverride() {
            return override.get();
        }

        @Override
        public ModelProfile profile() {
            final ModelProfile defaults = ModelProfile.defaults();
            return new ModelProfile(
                    defaults.reasoning(),
                    Set.of(),
                    defaults.systemRole(),
                    defaults.quirks(),
                    defaults.sampling(),
                    defaults.thinking(),
                    new ContextPolicy(HARD_CAP, OUTPUT_MAX, detected, loaded),
                    0,
                    defaults.batchSize(),
                    defaults.tier());
        }
    }

    // IF the override is saved after the chunk was entered, THEN the chunk keeps its window until the next entry.
    @Test
    void enterChunk_overrideSavedAfterEntry_currentKeepsOldUntilNextEntry() {
        final Windowed model = new Windowed(null, null);
        final RunWindow window = new RunWindow(model, null);

        window.enterChunk();
        model.override.set(8192);

        assertThat(window.current()).isEqualTo(16384);
        window.enterChunk();
        assertThat(window.current()).isEqualTo(8192);
    }

    @Test
    void enterChunk_noOverride_usesCappedDetected() {
        final RunWindow window = new RunWindow(new Windowed(131072, null), null);

        window.enterChunk();

        assertThat(window.current()).isEqualTo(16384);
    }

    // A server that fixed the window at load (LM Studio, llama-server) is planned for what it loaded.
    @Test
    void enterChunk_loadedAt8192_usesTheLoadedWindow() {
        final RunWindow window = new RunWindow(new Windowed(131072, 8192), null);

        window.enterChunk();

        assertThat(window.current()).isEqualTo(8192);
    }

    // A context length read at Start counts where the model's profile knows none.
    @Test
    void enterChunk_onlyTheCallerReadALength_usesIt() {
        final RunWindow window = new RunWindow(new Windowed(null, null), 4096);

        window.enterChunk();

        assertThat(window.current()).isEqualTo(4096);
    }

    @Test
    void enterChunk_overrideAboveTheCap_overrideWins() {
        final Windowed model = new Windowed(4096, null);
        model.override.set(65536);
        final RunWindow window = new RunWindow(model, null);

        window.enterChunk();

        assertThat(window.current()).isEqualTo(65536);
    }
}
