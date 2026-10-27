package ua.bookloom.ui.i18n;

import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The language choice read from storage once at startup, held where {@link OsLocaleProvider} can read it from any
 * thread without touching the store. Nothing is stored here: the stored setting stays the source of truth, this is
 * only its early copy.
 */
@Slf4j
@Singleton
public final class StoredLanguage {

    private volatile @Nullable String tag;
    private volatile boolean seeded;

    /** Created by Guice; nothing is known until {@link #seed} runs. */
    public StoredLanguage() {
        // empty until seeded
    }

    /**
     * Records what the store held.
     *
     * @param stored {@code uk} or {@code en}, or empty when the person has not chosen a language
     */
    public void seed(final Optional<String> stored) {
        tag = stored.orElse(null);
        seeded = true;
        log.debug("stored language seeded: {}", stored.orElse("none"));
    }

    /**
     * The stored choice.
     *
     * @return {@code uk} or {@code en}, or empty when none is stored or {@link #seed} has not run yet
     */
    public Optional<String> get() {
        return Optional.ofNullable(tag);
    }

    /**
     * Whether the store has been read.
     *
     * @return {@code true} once {@link #seed} has run
     */
    public boolean isSeeded() {
        return seeded;
    }
}
