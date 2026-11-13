package ua.bookloom.llm.profile;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether a model runs on someone else's hardware. A local model is held to a small window because its memory is the
 * person's; a hosted one is not, so it gets its own window and room to think. The address decides, with no name
 * lookup: loopback, a private address range, a host with no dot and {@code .local} are the person's own network; every
 * other host is hosted. A cloud model that a local Ollama relays ({@code gpt-oss:120b-cloud}) is hosted whatever the
 * address says.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModelHosting {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}");
    private static final int PRIVATE_172_FIRST = 16;
    private static final int PRIVATE_172_LAST = 31;

    /**
     * Decides where the model runs.
     *
     * @param baseUrl the provider's address; non-null
     * @param modelId the model id as the provider names it; non-null
     * @return {@code true} if the model runs on the provider's hardware, {@code false} if on the person's network
     */
    public static boolean isHosted(final URI baseUrl, final String modelId) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(modelId, "modelId");
        final String lowerModel = modelId.toLowerCase(Locale.ROOT);
        final boolean cloudModel = lowerModel.endsWith("-cloud") || lowerModel.endsWith(":cloud");
        final String host = baseUrl.getHost() == null ? "" : baseUrl.getHost().toLowerCase(Locale.ROOT);
        final boolean hosted = cloudModel || !isOwnNetwork(host);
        log.debug("Model hosting host={} cloudModel={} hosted={}", host, cloudModel, hosted);
        return hosted;
    }

    private static boolean isOwnNetwork(final String host) {
        final String bare = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        if (bare.isEmpty() || "localhost".equals(bare) || "::1".equals(bare) || bare.endsWith(".local")) {
            return true;
        }
        final var matcher = IPV4.matcher(bare);
        if (matcher.matches()) {
            final int first = Integer.parseInt(matcher.group(1));
            final int second = Integer.parseInt(matcher.group(2));
            return first == 127
                    || first == 10
                    || (first == 192 && second == 168)
                    || (first == 172 && second >= PRIVATE_172_FIRST && second <= PRIVATE_172_LAST);
        }
        return !bare.contains(".");
    }
}
