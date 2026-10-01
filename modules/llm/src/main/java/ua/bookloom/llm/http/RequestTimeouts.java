package ua.bookloom.llm.http;

import java.time.Duration;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ProviderConfig;

/**
 * Chooses a chat call's request timeout by what the call is for. The judge and the helper calls have a capped,
 * short reply, so their own bound ends a stuck call long before the provider's flat timeout would; every other call
 * scales with its expected output, never below the provider's configured timeout.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestTimeouts {

    private static final long MILLIS_PER_EXPECTED_TOKEN = 500;
    private static final Duration MAX_CHAT_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration JUDGE_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration HELPER_TIMEOUT = Duration.ofMinutes(2);
    // A streaming reply that sends nothing for this long has stalled, however long the whole call may take.
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Returns a copy of {@code config} whose request timeout fits {@code request}.
     *
     * @param config the provider config a chat call is about to be sent with
     * @param request the call; its kind and expected output choose the timeout
     * @return a copy whose timeout is 90 s for a judge call, 120 s for a prescan or summary call, and otherwise
     *     {@code max(config.requestTimeout(), min(600s, expectedOutputTokens * 500ms))}; {@code config} unchanged when
     *     the call has neither one of those kinds nor an expected output
     */
    public static ProviderConfig forChat(ProviderConfig config, ChatRequest request) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(request, "request");
        final Duration fixed = fixedFor(request);
        final ProviderConfig chosen =
                fixed == null ? scaled(config, request.expectedOutputTokens()) : config.withRequestTimeout(fixed);
        log.debug(
                "Chat timeout chosen kind={} expectedOutputTokens={} configured={} effective={}",
                request.callKind(),
                request.expectedOutputTokens(),
                config.requestTimeout(),
                chosen.requestTimeout());
        return chosen;
    }

    /**
     * The longest a streaming reply may send nothing before the call counts as stalled. The whole call is still bound
     * by the timeout {@link #forChat} chose.
     *
     * @param providerConfig the provider's own config, before {@link #forChat} chose the call's timeout
     * @return sixty seconds, or the provider's configured timeout when that is shorter
     */
    public static Duration streamIdle(ProviderConfig providerConfig) {
        Objects.requireNonNull(providerConfig, "providerConfig");
        return providerConfig.requestTimeout().compareTo(STREAM_IDLE_TIMEOUT) < 0
                ? providerConfig.requestTimeout()
                : STREAM_IDLE_TIMEOUT;
    }

    private static @Nullable Duration fixedFor(ChatRequest request) {
        if (request.callKind() == null) {
            return null;
        }
        return switch (request.callKind()) {
            case JUDGE -> JUDGE_TIMEOUT;
            case PRESCAN, SUMMARY -> HELPER_TIMEOUT;
            case DRAFT, STRUCTURAL_REPAIR, PLACEHOLDER_REPAIR, DIRECTED_FIX, REFLECT, IMPROVE, POLISH, REVISION -> null;
        };
    }

    private static ProviderConfig scaled(ProviderConfig config, @Nullable Integer expectedOutputTokens) {
        if (expectedOutputTokens == null) {
            return config;
        }
        final Duration scaled = Duration.ofMillis(expectedOutputTokens * MILLIS_PER_EXPECTED_TOKEN);
        final Duration capped = scaled.compareTo(MAX_CHAT_TIMEOUT) > 0 ? MAX_CHAT_TIMEOUT : scaled;
        final Duration effective = capped.compareTo(config.requestTimeout()) > 0 ? capped : config.requestTimeout();
        return config.withRequestTimeout(effective);
    }
}
