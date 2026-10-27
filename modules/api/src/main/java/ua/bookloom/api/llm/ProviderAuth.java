package ua.bookloom.api.llm;

/** How a provider expects a request to sign in. */
public enum ProviderAuth {
    /** No credential is sent. */
    NONE,
    /** The credential goes in an {@code Authorization: Bearer} header. */
    BEARER,
    /** The credential goes in a header whose name the description gives. */
    API_KEY
}
