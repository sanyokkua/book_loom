package ua.bookloom.ui.state;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;

/** How the registry's providers become the settings list: the order they are listed in and the row of each. */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below
// (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProviderRows {

    static List<ProviderConfig> sorted(final ProviderConfigs configs) {
        return configs.all().stream()
                .sorted(Comparator.comparing(ProviderConfig::kind).thenComparing(ProviderConfig::id))
                .toList();
    }

    static ProviderRow rowOf(final ProviderConfig config) {
        final URI url = config.baseUrl();
        final String hostPort = url.getPort() < 0 ? url.getHost() : url.getHost() + ":" + url.getPort();
        final String given = config.displayName();
        return new ProviderRow(config.id(), config.kind(), url.toString(), hostPort, given == null ? "" : given);
    }
}
