package ua.bookloom.pipeline.archfixture;

import java.net.http.HttpClient;

/**
 * Violation fixture for {@code no-http-in-core-except-llm}: a {@code :pipeline} class able to open a socket. This
 * rule is the structural seed of the offline invariant (F9, DD-01) — "only one module can open a socket" has to
 * be a compile-gate fact, and a second module acquiring an {@code HttpClient} is exactly how it stops being one.
 */
public final class HttpUsingCoordinator {

    public HttpClient client() {
        return HttpClient.newHttpClient();
    }
}
