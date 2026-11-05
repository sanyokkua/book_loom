package ua.bookloom.pipeline.project;

import static ua.bookloom.util.contract.ArgNames.PROJECT_ID;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.SourceLanguage;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * The one place an opened {@link Document} lives, with the structure tree its inspection derived, so every later
 * service reads the same parsed book, and the run finds the same chapters, by project id.
 */
@Slf4j
@Singleton
public final class OpenProjects {

    private final Map<String, Opened> opened = new ConcurrentHashMap<>();

    /** A project's document, its structure and its index, built on first use; a new put starts all afresh. */
    private record Opened(
            Document document, List<StructureNode> structure, AtomicReference<@Nullable BookIndex> index) {}

    /**
     * Holds a document opened without an inspection, as a test opens one, with a structure that lists each of its
     * units whole; the first {@link #index} call builds its index.
     *
     * @param projectId the non-null project id
     * @param document the non-null opened document
     */
    public void put(final String projectId, final Document document) {
        Objects.requireNonNull(document, "document");
        final List<StructureNode> units = document.units().stream()
                .map(unit ->
                        new StructureNode(unit.id(), unit.id(), unit.segments().size(), List.of()))
                .toList();
        put(projectId, document, null, units);
    }

    /**
     * Holds the document opened for a project together with its structure and the index already built from it, and
     * drops what the book it replaces held.
     *
     * @param projectId the non-null project id
     * @param document the non-null opened document
     * @param index the index of exactly this document, or {@code null} to have it built on first use
     * @param structure the non-null structure tree the inspection derived from this document
     */
    public void put(
            final String projectId,
            final Document document,
            @Nullable final BookIndex index,
            final List<StructureNode> structure) {
        log.debug(
                "Holding the open book project={} indexBuilt={} topNodes={}",
                projectId,
                index != null,
                Objects.requireNonNull(structure, "structure").size());
        opened.put(
                Objects.requireNonNull(projectId, PROJECT_ID),
                new Opened(
                        Objects.requireNonNull(document, "document"),
                        List.copyOf(structure),
                        new AtomicReference<>(index)));
    }

    /**
     * Reads a project's opened document.
     *
     * @param projectId the non-null project id
     * @return the document, or {@code null} when none is open for that id
     */
    public @Nullable Document get(final String projectId) {
        final Opened found = opened.get(Objects.requireNonNull(projectId, PROJECT_ID));
        return found == null ? null : found.document();
    }

    /**
     * Finds the chapters of a project's opened book by the one chapter rule, so the run, a review retry and the
     * planned chunks all cut it the same way.
     *
     * @param projectId the non-null project id
     * @param brief the non-null Book Brief, whose source language the chapters' tokens are estimated in
     * @return the chapters, or {@code null} when no book is open for that id
     */
    public @Nullable ChapterBoundaries chapters(final String projectId, final BookBrief brief) {
        Objects.requireNonNull(brief, "brief");
        final Opened found = opened.get(Objects.requireNonNull(projectId, PROJECT_ID));
        if (found == null) {
            log.debug("No chapters for project={}: no book is open", projectId);
            return null;
        }
        return ChapterBoundaries.of(
                found.document(), found.structure(), SourceLanguage.forAnalysis(brief, found.document()));
    }

    /**
     * Forgets a project's opened document and its index.
     *
     * @param projectId the non-null project id
     * @return the document that was held, or {@code null} when none was
     */
    public @Nullable Document remove(final String projectId) {
        final Opened removed = opened.remove(Objects.requireNonNull(projectId, PROJECT_ID));
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
        final Opened found = opened.get(Objects.requireNonNull(projectId, PROJECT_ID));
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
        final Opened found = opened.get(Objects.requireNonNull(projectId, PROJECT_ID));
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
