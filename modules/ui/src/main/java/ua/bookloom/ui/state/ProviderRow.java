package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.llm.ProviderKind;

/**
 * One provider as the settings list shows it.
 *
 * @param id the registry key the row selects and the verifier is asked about
 * @param kind which wire dialect the provider speaks
 * @param endpoint the base URL as text, exactly as the registry holds it
 * @param hostPort the endpoint's host and port without scheme or path, e.g. {@code localhost:11434}; the host alone
 *     when the URL names no port
 */
public record ProviderRow(String id, ProviderKind kind, String endpoint, String hostPort) {

    /** Rejects a row that names no provider, kind or endpoint. */
    public ProviderRow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(hostPort, "hostPort");
    }
}
