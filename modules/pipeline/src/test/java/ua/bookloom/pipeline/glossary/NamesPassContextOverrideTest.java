package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.project.NamePolicy;

/** The names pass sizes its calls for the window the person set on the model, not the one the provider reported. */
class NamesPassContextOverrideTest extends NamesPassTestBase {

    private static final int OVERRIDE = 8192;
    private static final int DETECTED = 4096;

    // IF the model's stored window is 8192 and the provider reported 4096, THEN no call is sized for 4096 (the
    // lexicon reading is sized to the default window whatever was reported, so only the 4096 calls are checked).
    @Test
    void run_override8192Detected4096_noRequestIsSizedFor4096() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final Overriding model = new Overriding(model());

        fixture.pass().run(PROJECT, model, DETECTED, events::add);

        assertThat(model.windows).contains(OVERRIDE).doesNotContain(DETECTED);
    }

    /** Wraps the scripted names model and reports a stored window, as a bound model does. */
    private static final class Overriding implements ChatModel {

        private final NamesPassModel inner;
        private final @Nullable Integer override;
        private final List<@Nullable Integer> windows = new CopyOnWriteArrayList<>();

        Overriding(final NamesPassModel inner) {
            this(inner, OVERRIDE);
        }

        Overriding(final NamesPassModel inner, @Nullable final Integer override) {
            this.inner = inner;
            this.override = override;
        }

        @Override
        public @Nullable Integer contextWindowOverride() {
            return override;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            windows.add(request.contextWindow());
            return inner.chat(request);
        }
    }
}
