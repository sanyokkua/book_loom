package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Remembers, for the rest of the session, whether the person left each collapsible section of a screen open, keyed by
 * the section's node id, so a screen rebuilt on the next visit shows it as they left it.
 *
 * <p>A screen is rebuilt every time it is shown, and a fresh section starts collapsed; without this the "Context sent
 * to the model" section closed itself whenever the person looked at another step. The map is bounded, dropping the
 * section used longest ago, because ids are few and fixed but nothing should grow with a session. FX thread only.
 */
@Slf4j
@Singleton
public final class SectionMemory {

    private static final int MAX_SECTIONS = 32;
    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private final Map<String, Boolean> open = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
            return size() > MAX_SECTIONS;
        }
    };

    /** Creates an empty memory: every section starts collapsed. */
    @Inject
    public SectionMemory() {
        // Nothing to receive: the memory starts empty.
    }

    /**
     * Whether the person left a section open.
     *
     * @param id the section's node id
     * @return {@code true} if it was last left open, {@code false} if it was closed or never touched
     */
    public boolean isOpen(final String id) {
        Objects.requireNonNull(id, "id");
        return open.getOrDefault(id, false);
    }

    /**
     * Records that the person opened or closed a section.
     *
     * @param id the section's node id
     * @param isOpen whether it is now open
     */
    public void remember(final String id, final boolean isOpen) {
        Objects.requireNonNull(id, "id");
        log.debug("section {} left {}", id, isOpen ? "open" : "closed");
        open.put(id, isOpen);
    }
}
