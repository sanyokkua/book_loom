package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.SessionInfo;

/**
 * Tells the {@link SessionInfo} what the window is working on — the book when one is opened, the provider, model and
 * brief when a run starts — so the detailed log names them in its header and in one {@code session update} line each.
 *
 * <p>Only facts that are safe to share go in: the book's file <em>name</em>, never its folder; the endpoint's
 * <em>host and port</em>, never the URL's user part, path or query; the brief's choices, never the person's free text
 * (audience, voice era), which may quote the book.
 */
@Slf4j
@Singleton
public final class SessionReporter {

    private static final String NONE = "-";

    private final SessionInfo session;
    private final ProviderConfigs configs;

    /**
     * Creates the reporter and starts following the open book. Bound as an eager singleton, so the first book opened is
     * reported even before any screen asks for this.
     *
     * @param session the facts the detailed log names
     * @param current the open book, whose every change of book is reported
     * @param configs the provider descriptions an endpoint host is read from
     */
    @Inject
    public SessionReporter(final SessionInfo session, final CurrentProject current, final ProviderConfigs configs) {
        this.session = Objects.requireNonNull(session, "session");
        this.configs = Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(current, "current").book().addListener((observed, old, book) -> {
            if (book != null) {
                bookOpened(book);
            }
        });
    }

    /**
     * Records the opened book: its file name, format and size.
     *
     * @param book the book the window has just opened
     */
    void bookOpened(final OpenedBook book) {
        Objects.requireNonNull(book, "book");
        log.debug("reporting the opened book {} to the session", book.projectId());
        final Map<String, String> facts = new LinkedHashMap<>();
        facts.put("book", fileName(book.source()));
        facts.put("format", formatOf(book.inspection().format()));
        final BookProfile profile = book.profile();
        facts.put(
                "segments",
                profile == null ? NONE : Integer.toString(profile.stats().segments()));
        facts.put(
                "words",
                profile == null ? NONE : Integer.toString(profile.stats().words()));
        session.update(facts);
    }

    /**
     * Records the run about to start: the provider, its endpoint host, the model, and the brief's choices.
     *
     * @param context the run being started
     * @param brief the brief as the person has left it
     */
    public void runStarting(final RunContext context, final BookBrief brief) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(brief, "brief");
        log.debug("reporting the run on {} to the session", context.projectId());
        final ProviderConfig config =
                configs.find(context.selection().providerId()).orElse(null);
        final Map<String, String> facts = new LinkedHashMap<>();
        facts.put("provider", context.selection().providerId());
        facts.put("providerKind", config == null ? NONE : config.kind().name());
        facts.put("endpointHost", config == null ? NONE : hostOf(config.baseUrl()));
        facts.put("model", context.selection().modelId());
        facts.put("dial", context.dial().name());
        facts.put("reviewMode", context.reviewMode().name());
        facts.putAll(briefFacts(brief));
        session.update(facts);
    }

    private static Map<String, String> briefFacts(final BookBrief brief) {
        final Map<String, String> facts = new LinkedHashMap<>();
        facts.put("sourceLanguage", orNone(brief.sourceLanguage()));
        facts.put("targetLanguage", orNone(brief.targetLanguage()));
        facts.put("register", brief.register().name());
        facts.put("names", brief.names().name());
        facts.put("foreignPassages", brief.foreignPassages().name());
        facts.put("footnotes", brief.footnotes().name());
        facts.put("units", brief.units().name());
        facts.put("balance", Integer.toString(brief.balance()));
        return facts;
    }

    /** The host and, when given, the port: the one network destination, without anything a URL may carry beyond it. */
    static String hostOf(final URI endpoint) {
        final String host = endpoint.getHost();
        if (host == null) {
            return NONE;
        }
        return endpoint.getPort() < 0 ? host : host + ":" + endpoint.getPort();
    }

    private static String fileName(final Path source) {
        final Path name = source.getFileName();
        return name == null ? NONE : name.toString();
    }

    private static String formatOf(final @Nullable BookFormat format) {
        return format == null ? NONE : format.name();
    }

    private static String orNone(final @Nullable String value) {
        return value == null || value.isBlank() ? NONE : value;
    }
}
