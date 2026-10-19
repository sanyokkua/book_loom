package ua.bookloom.app.bootstrap;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.paths.AppEnvironment;

/**
 * The block written at the top of every detailed-log file, so one file is self-explanatory when shared: the build, the
 * machine, the log settings, and the session facts known at the moment the file was opened.
 *
 * <p>Rendered afresh each time a file is opened — at start and after every rotation — which is why the session facts
 * are a supplier rather than a value: a file opened mid-run names the provider, model and book of that run. Every line
 * starts with {@code #} so the block reads as a comment between ordinary log lines. It carries no path beyond the log
 * file's own folder, no credential and no book text.
 *
 * @param version the build version
 * @param environment the resolved run environment
 * @param level the resolved level of the ordinary log
 * @param trace the resolved detailed-log switch
 * @param properties reads a system property, returning {@code null} when unset
 * @param session the facts recorded so far this session, in first-seen order
 */
public record SessionHeader(
        String version,
        AppEnvironment environment,
        ResolvedLogLevel level,
        ResolvedTraceFile trace,
        Function<String, @Nullable String> properties,
        Supplier<Map<String, String>> session) {

    private static final String UNKNOWN = "unknown";

    /** Rejects a missing component. */
    public SessionHeader {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(session, "session");
    }

    /**
     * Renders the block.
     *
     * @return the header lines, each starting with {@code # }, separated by line breaks and without a trailing one
     */
    public String render() {
        final String facts = session.get().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" "));
        return String.join(
                System.lineSeparator(),
                "# ==== BookLoom diagnostic log (TRACE; contains book text, stays on this computer) ====",
                "# version=" + version + " environment=" + environment,
                "# os=" + property("os.name") + " " + property("os.version") + " " + property("os.arch"),
                "# jvm=" + property("java.vm.name") + " " + property("java.runtime.version"),
                "# javafx=" + property("javafx.runtime.version") + " locale="
                        + Locale.getDefault().toLanguageTag(),
                "# logLevel=" + level.level() + " (" + level.source() + ") detailedLog="
                        + (trace.enabled() ? "on" : "off") + " (" + trace.source() + ")",
                "# session " + (facts.isEmpty() ? "(no book opened yet)" : facts));
    }

    private String property(final String name) {
        final String value = properties.apply(name);
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
