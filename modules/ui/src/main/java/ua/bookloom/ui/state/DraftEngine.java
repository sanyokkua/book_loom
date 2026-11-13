package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;

/**
 * What the Providers form knows about the program behind the address, kept with the draft so a Save writes it back
 * and never drops what a detection or the person said.
 *
 * @param detected the engine the last detection found; the kind's own engine before any detection
 * @param choice the person's "Treat this server as…" choice, which detection never overwrites; null for none
 * @param version the version the server reported, or null when it did not say
 * @param offerDeclined whether the person kept an OpenAI-compatible connection to Ollama ("Keep as is"), so the
 *     offer of Ollama's own connection never returns
 */
public record DraftEngine(
        ServerEngine detected,
        @Nullable ServerEngine choice,
        @Nullable String version,
        boolean offerDeclined) {

    /** Rejects a missing detected engine. */
    public DraftEngine {
        Objects.requireNonNull(detected, "detected");
    }

    /**
     * The engine a new provider of one kind starts with, before any detection.
     *
     * @param kind the dialect
     * @return Ollama for Ollama's own connection, else a generic server; no choice, no version, the offer open
     */
    public static DraftEngine forKind(final ProviderKind kind) {
        Objects.requireNonNull(kind, "kind");
        return new DraftEngine(
                kind == ProviderKind.OLLAMA ? ServerEngine.OLLAMA : ServerEngine.GENERIC, null, null, false);
    }

    /**
     * The engine facts a stored provider holds.
     *
     * @param config the stored description
     * @return its four engine facts
     */
    public static DraftEngine of(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        return new DraftEngine(
                config.engine(), config.engineChoice(), config.engineVersion(), config.nativeOfferDeclined());
    }

    /**
     * The engine requests are shaped for.
     *
     * @return the person's choice when set, else the detected engine
     */
    public ServerEngine effective() {
        return choice != null ? choice : detected;
    }

    /**
     * What a detection found, the person's choice kept.
     *
     * @param engine the engine found
     * @param foundVersion the version it reported, or null
     * @return the facts with only the detected engine and version changed
     */
    public DraftEngine found(final ServerEngine engine, final @Nullable String foundVersion) {
        return new DraftEngine(engine, choice, foundVersion, offerDeclined);
    }

    /**
     * What Detect again stores: the probes decide, so the person's choice goes; a declined offer stays declined.
     *
     * @param engine the engine found
     * @param foundVersion the version it reported, or null
     * @return the facts with the detected engine and version changed and no choice
     */
    public DraftEngine foundAgain(final ServerEngine engine, final @Nullable String foundVersion) {
        return new DraftEngine(engine, null, foundVersion, offerDeclined);
    }

    /**
     * The person's "Treat this server as…" choice.
     *
     * @param chosen the engine, or null to go back to the detected one
     * @return the facts with only the choice changed
     */
    public DraftEngine withChoice(final @Nullable ServerEngine chosen) {
        return new DraftEngine(detected, chosen, version, offerDeclined);
    }

    /**
     * Keep as is on the offer of Ollama's own connection: the offer is declined and nothing else changes, so the
     * engine stays the detected Ollama and no choice is made.
     *
     * @return the facts with the offer declined
     */
    public DraftEngine declined() {
        return new DraftEngine(detected, choice, version, true);
    }
}
