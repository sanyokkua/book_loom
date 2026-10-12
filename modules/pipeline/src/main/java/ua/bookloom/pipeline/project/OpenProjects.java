package ua.bookloom.pipeline.project;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.pipeline.index.BookIndex;

/** The one place an opened {@link Document} lives, so every later service reads the same parsed book by project id. */
@Singleton
public final class OpenProjects {

    private final Map<String, Opened> opened = new ConcurrentHashMap<>();

    /** A project's document and its index, built on first use; a new put starts both afresh. */
    private record Opened(Document document, AtomicReference<@Nullable BookIndex> index) {}

    /**
     * Holds the document opened for a project and drops the index of the book it replaces.
     *
     * @param projectId the non-null project id
     * @param document the non-null opened document
     */
    public void put(final String projectId, final Document document) {
        opened.put(
                Objects.requireNonNull(projectId, "projectId"),
                new Opened(Objects.requireNonNull(document, "document"), new AtomicReference<>()));
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
        return removed == null ? null : removed.document();
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
            return null;
        }
        BookIndex built = found.index().get();
        if (built == null) {
            found.index().compareAndSet(null, BookIndex.of(found.document()));
            built = found.index().get();
        }
        return built;
    }
}
