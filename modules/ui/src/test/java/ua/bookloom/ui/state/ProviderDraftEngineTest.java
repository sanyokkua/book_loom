package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;

/** The engine facts a draft keeps between a stored provider and its next Save. */
class ProviderDraftEngineTest {

    private static ProviderConfig config(final ProviderDraft draft, final String id) {
        return Objects.requireNonNull(draft.toConfig(id).data(), "the draft is valid");
    }

    // IF a Save from the form dropped what detection found and the person chose, THEN every save would reset the
    // engine to a generic server, erase "Treat this server as…" and bring the declined Ollama offer back.
    @Test
    void providerDraft_save_keepsEngineChoiceVersionAndDeclinedOffer() {
        final ProviderConfig declined = new ProviderConfig(
                "x",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:11434/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT,
                "Mine",
                ProviderAuth.NONE,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                ServerEngine.OLLAMA,
                ServerEngine.LLAMA_CPP,
                "0.40.11",
                true);

        final ProviderConfig saved = config(ProviderDraft.of(declined, "").withRequestSeconds("240"), "x");

        assertThat(saved.engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(saved.engineChoice()).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(saved.engineVersion()).isEqualTo("0.40.11");
        assertThat(saved.nativeOfferDeclined()).isTrue();
        assertThat(saved.effectiveEngine()).isEqualTo(ServerEngine.LLAMA_CPP);
    }

    // A new provider starts from its kind's own engine, with nothing chosen and the offer open.
    @Test
    void blank_eachKind_startsFromTheKindsEngine() {
        assertThat(config(ProviderDraft.blank(ProviderKind.OLLAMA).withName("a"), "a")
                        .engine())
                .isEqualTo(ServerEngine.OLLAMA);
        assertThat(config(ProviderDraft.blank(ProviderKind.OPENAI_COMPATIBLE).withName("b"), "b")
                        .engine())
                .isEqualTo(ServerEngine.GENERIC);
    }
}
