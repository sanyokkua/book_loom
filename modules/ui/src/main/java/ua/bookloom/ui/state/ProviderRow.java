package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * One provider as the settings list shows it.
 *
 * @param id the registry key the row selects and the verifier is asked about
 * @param endpoint the base URL as text, exactly as the registry holds it
 */
public record ProviderRow(String id, String endpoint) {

    /** Rejects a row that names no provider or no endpoint. */
    public ProviderRow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(endpoint, "endpoint");
    }
}
