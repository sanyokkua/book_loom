package ua.bookloom.ui;

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * The facts about this session that make a shared diagnostic log self-explanatory: the provider and model, the endpoint
 * host, the brief's choices, the open book's file name and format, the JavaFX version once the toolkit is up.
 *
 * <p>Every change is written once as an INFO {@code session update} line, and the detailed log's header (written at
 * the top of each file, again after every rotation) reads {@link #snapshot()}, so a file cut by rotation still names
 * the session it belongs to. Values are short, single-line facts: a caller passes a file <em>name</em>, never a path,
 * and an endpoint <em>host</em>, never a URL with credentials — nothing here is ever a secret or book text.
 *
 * <p>Thread-safe without locks: the map is replaced whole through an {@link AtomicReference}, so the logging thread
 * rendering a header never sees a half-applied update.
 */
@Slf4j
@Singleton
public final class SessionInfo {

    /** A value longer than this is cut: the header is a summary, not a place for text. */
    static final int MAX_VALUE_LENGTH = 120;

    private final AtomicReference<Map<String, String>> values = new AtomicReference<>(Map.of());

    /** Starts empty; the launcher creates the instance once logging is configured, a test lets Guice make one. */
    public SessionInfo() {
        // Nothing to set: values arrive through update().
    }

    /**
     * Records new or changed facts and writes them as one INFO line. Keys keep their first-seen order.
     *
     * @param changes the facts to set, by key; an empty map changes nothing and writes nothing
     */
    public void update(final Map<String, String> changes) {
        Objects.requireNonNull(changes, "changes");
        if (changes.isEmpty()) {
            log.debug("session update skipped: nothing changed");
            return;
        }
        final Map<String, String> cleaned = new LinkedHashMap<>();
        changes.forEach((key, value) -> cleaned.put(clean(key), clean(value)));
        values.updateAndGet(current -> merged(current, cleaned));
        log.info("session update {}", format(cleaned));
    }

    /**
     * The facts recorded so far.
     *
     * @return an unmodifiable copy in first-seen key order; empty before the first update
     */
    public Map<String, String> snapshot() {
        return Objects.requireNonNull(values.get(), "values");
    }

    /**
     * Renders facts as {@code key=value} pairs separated by spaces, the shape every log line in the project uses.
     *
     * @param facts the facts to render
     * @return the pairs; empty for no facts
     */
    public static String format(final Map<String, String> facts) {
        Objects.requireNonNull(facts, "facts");
        return facts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" "));
    }

    private static Map<String, String> merged(final Map<String, String> current, final Map<String, String> changes) {
        final Map<String, String> next = new LinkedHashMap<>(current);
        next.putAll(changes);
        return Collections.unmodifiableMap(next);
    }

    // One line per fact, bounded: a value with a line break would forge a log line of its own.
    private static String clean(final String raw) {
        final String single = raw.replaceAll("\\s+", " ").strip();
        return single.length() <= MAX_VALUE_LENGTH ? single : single.substring(0, MAX_VALUE_LENGTH) + "…";
    }
}
