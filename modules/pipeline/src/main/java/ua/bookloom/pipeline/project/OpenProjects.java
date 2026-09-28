package ua.bookloom.pipeline.project;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;

/** The one place an opened {@link Document} lives, so every later service reads the same parsed book by project id. */
@Singleton
public final class OpenProjects {

    private final Map<String, Document> documents = new ConcurrentHashMap<>();

    /**
     * Holds the document opened for a project.
     *
     * @param projectId the non-null project id
     * @param document the non-null opened document
     */
    public void put(final String projectId, final Document document) {
        documents.put(Objects.requireNonNull(projectId, "projectId"), Objects.requireNonNull(document, "document"));
    }

    /**
     * Reads a project's opened document.
     *
     * @param projectId the non-null project id
     * @return the document, or {@code null} when no book is open for that id
     */
    public @Nullable Document get(final String projectId) {
        return documents.get(Objects.requireNonNull(projectId, "projectId"));
    }

    /**
     * Forgets a project's opened document.
     *
     * @param projectId the non-null project id
     * @return the document that was open, or {@code null} when none was
     */
    public @Nullable Document remove(final String projectId) {
        return documents.remove(Objects.requireNonNull(projectId, "projectId"));
    }
}
