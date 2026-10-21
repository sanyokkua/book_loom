package ua.bookloom.llm.provider;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ProviderConfig;

/**
 * Picks the path a request goes to: the description's override when it gives one, else the dialect's default. An
 * override replaces the path only; the base URL, the sign-in and the dialect's request shape stay as they are.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EndpointPaths {

    /**
     * Chooses the model-list path.
     *
     * @param config the provider description
     * @param defaultPath the dialect's own model-list path
     * @return the override when it is not blank, else {@code defaultPath}
     */
    public static String modelsPath(ProviderConfig config, String defaultPath) {
        Objects.requireNonNull(config, "config");
        return choose("models", config.modelsPath(), defaultPath);
    }

    /**
     * Chooses the chat path.
     *
     * @param config the provider description
     * @param defaultPath the dialect's own chat path
     * @return the override when it is not blank, else {@code defaultPath}
     */
    public static String chatPath(ProviderConfig config, String defaultPath) {
        Objects.requireNonNull(config, "config");
        return choose("chat", config.chatPath(), defaultPath);
    }

    private static String choose(String purpose, @Nullable String override, String defaultPath) {
        if (override == null || override.isBlank()) {
            return defaultPath;
        }
        log.debug("Using endpoint path override purpose={} path={} default={}", purpose, override, defaultPath);
        return override;
    }
}
