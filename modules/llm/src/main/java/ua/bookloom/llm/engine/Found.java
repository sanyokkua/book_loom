package ua.bookloom.llm.engine;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ServerEngine;

/**
 * What one detection step recognised, before the probe count and the offer are added.
 *
 * @param engine the engine recognised
 * @param version the version the server reported, or null
 * @param router whether a llama-server serves several models by id
 * @param suitability whether the app can translate a book
 */
record Found(ServerEngine engine, @Nullable String version, boolean router, Suitability suitability) {

    Found {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(suitability, "suitability");
    }

    /** A usable engine with no version or router flag. */
    static Found of(final ServerEngine engine) {
        return new Found(engine, null, false, Suitability.SUITABLE);
    }
}
