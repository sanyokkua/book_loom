package ua.bookloom.llm;

import java.net.URI;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A loopback endpoint nothing listens on, for the "provider is unreachable" cases.
 *
 * <p>The old helpers bound a port with {@code new ServerSocket(0)} and released it, which leaves a window in which
 * another server — a WireMock in this JVM or in a parallel test fork, all of which take dynamic ports from the same
 * ephemeral range — can take that port and answer the request the test expects to be refused. Port 1 (tcpmux) lies
 * below every operating system's ephemeral range, so no dynamically bound test server can ever be given it, and
 * nothing listens on it on a development machine or a CI runner; a connection to it is refused at once. (Holding a
 * bound, non-listening socket instead does not work: macOS drops the connection attempt and the call times out.)
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClosedPorts {

    private static final int NEVER_EPHEMERAL_PORT = 1;

    /**
     * Names the closed endpoint.
     *
     * @param path appended after the port, e.g. {@code "/v1"}, or empty
     * @return {@code http://127.0.0.1:1<path>}
     */
    public static URI endpoint(final String path) {
        return URI.create("http://127.0.0.1:" + NEVER_EPHEMERAL_PORT + path);
    }
}
