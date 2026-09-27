package ua.bookloom.llm.http;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ProviderConfig;

/** Scales a chat call's request timeout with its expected output, never below the provider's configured timeout. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class RequestTimeouts {

    private RequestTimeouts() {}

    private static final long MILLIS_PER_EXPECTED_TOKEN = 500;
    private static final Duration MAX_CHAT_TIMEOUT = Duration.ofMinutes(10);

    /**
     * Returns a copy of {@code config} whose request timeout is scaled to {@code expectedOutputTokens}.
     *
     * @param config the provider config a chat call is about to be sent with
     * @param expectedOutputTokens the call's expected completion length, or null when not known
     * @return {@code config} unchanged when {@code expectedOutputTokens} is null; otherwise a copy whose request
     *     timeout is {@code max(config.requestTimeout(), min(600s, expectedOutputTokens * 500ms))}
     */
    public static ProviderConfig forChat(ProviderConfig config, @Nullable Integer expectedOutputTokens) {
        Objects.requireNonNull(config, "config");
        if (expectedOutputTokens == null) {
            return config;
        }
        final Duration scaled = Duration.ofMillis(expectedOutputTokens * MILLIS_PER_EXPECTED_TOKEN);
        final Duration capped = scaled.compareTo(MAX_CHAT_TIMEOUT) > 0 ? MAX_CHAT_TIMEOUT : scaled;
        final Duration effective = capped.compareTo(config.requestTimeout()) > 0 ? capped : config.requestTimeout();
        return config.withRequestTimeout(effective);
    }
}
