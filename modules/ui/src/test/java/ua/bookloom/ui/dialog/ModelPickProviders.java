package ua.bookloom.ui.dialog;

import java.net.URI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;

/** The two providers the first-launch look can open the model-pick card with, as it stores them. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModelPickProviders {

    /** Ollama's own connection at its default address. */
    public static ProviderConfig ollama() {
        return new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://127.0.0.1:11434"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }

    /** LM Studio reached through the OpenAI-compatible dialect, its rows naming it. */
    public static ProviderConfig lmStudio() {
        return new ProviderConfig(
                        "lmstudio",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create("http://127.0.0.1:1234/v1"),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT)
                .withEngine(ServerEngine.LM_STUDIO);
    }
}
