package ua.bookloom.pipeline.project;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.pipeline.index.BookIndex;

/** The one place an opened {@link Document} lives, so every later service reads the same parsed book by project id. */
@Slf4j
@Singleton
public final class OpenProjects {

    private final Map<String, Opened> opened = new ConcurrentHashMap<>();

    /** A project's document and its index, built on first use; a new put starts both afresh. */
    private record Opened(Document document, AtomicReference<@Nullable BookIndex> index) {}

    /**
     * Holds the document opened for a project and drops the index of the book it replaces; the first
     * {@link #index} call builds the new one.
     *
     * @param projectId the non-null project id
     * @param document the non-null opened document
     */
    public void put(final String projectId, final Document document) {
        put(projectId, document, null);
    }

    /**
     * Holds the document opened for a project together with the index already built from it.
     *
     * @param projectId the non-null project id
     * @param document the non-null opened document
     * @param index the index of exactly this document, or {@code null} to have it built on first use
     */
    public void put(final String projectId, final Document document, @Nullable final BookIndex index) {
        log.debug("Holding the open book project={} indexBuilt={}", projectId, index != null);
        opened.put(
                Objects.requireNonNull(projectId, "projectId"),
                new Opened(Objects.requireNonNull(document, "document"), new AtomicReference<>(index)));
    }

    /**
     * Reads a project's opened document.
     *
     * @param projectId the non-null project id
     * @return the document, or {@code null} when none is open for that id
     */
    public @Nullable Document get(final String projectId) {
        final Opened found = opened.get(Objects.requireNonNull(projectId, "projectId"));
        return found == null ? null : found.document();
    }

    /**
     * Forgets a project's opened document and its index.
     *
     * @param projectId the non-null project id
     * @return the document that was held, or {@code null} when none was
     */
    public @Nullable Document remove(final String projectId) {
        final Opened removed = opened.remove(Objects.requireNonNull(projectId, "projectId"));
        log.debug("Forgetting the open book project={} wasOpen={}", projectId, removed != null);
        return removed == null ? null : removed.document();
    }

    /**
     * Tells whether a project's book index has been built.
     *
     * @param projectId the non-null project id
     * @return {@code true} if a book is open for the id and its index exists, {@code false} otherwise
     */
    public boolean hasIndex(final String projectId) {
        final Opened found = opened.get(Objects.requireNonNull(projectId, "projectId"));
        return found != null && found.index().get() != null;
    }

    /**
     * Reads a project's book index, building it from the held document the first time it is asked for (a document put
     * without an import has none yet). Two threads asking at once may both build; they get the same index back.
     *
     * @param projectId the non-null project id
     * @return the index, or {@code null} when no book is open for that id
     */
    public @Nullable BookIndex index(final String projectId) {
        final Opened found = opened.get(Objects.requireNonNull(projectId, "projectId"));
        if (found == null) {
            log.debug("No index for project={}: no book is open", projectId);
            return null;
        }
        BookIndex built = found.index().get();
        if (built == null) {
            log.debug("Building the index of project={} on first use", projectId);
            found.index().compareAndSet(null, BookIndex.of(found.document()));
            built = found.index().get();
        } else {
            log.debug("Serving the cached index of project={}", projectId);
        }
        return built;
    }
}
