package ua.bookloom.ui.state;

import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Which hosts are this computer: where a provider runs decides whether a closed app explains silence and whether the
 * book's text leaves the machine, so both questions read the same list.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoopbackHosts {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    /**
     * Whether a host is this computer.
     *
     * @param host the host of an address, any letter case, or {@code null} when the address names none
     * @return {@code true} for {@code localhost}, {@code 127.0.0.1} or {@code ::1}, {@code false} otherwise
     */
    public static boolean isLoopback(final @Nullable String host) {
        return host != null && LOOPBACK.contains(host.toLowerCase(Locale.ROOT));
    }
}
