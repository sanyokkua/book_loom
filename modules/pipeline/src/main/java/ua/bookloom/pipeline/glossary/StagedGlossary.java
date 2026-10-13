package ua.bookloom.pipeline.glossary;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * A private, in-memory copy of one project's glossary that the names pass edits instead of the stored one (15i.N8), so
 * a Stop or a failed call leaves the stored glossary exactly as the person left it. It keeps the stored repository's
 * rules — terms matched by {@link GlossaryKeys}, entries in insertion order, a duplicate refused — and also answers
 * for a name the person removed earlier from the stored repository, so a scan on the copy does not add it back. Used
 * from the pass's calling thread only; the project id of every call is that of the copied project.
 */
@Slf4j
final class StagedGlossary implements GlossaryRepository {

    private final GlossaryRepository stored;
    private final Map<String, GlossaryEntry> byId = new LinkedHashMap<>();
    private final Set<String> removedKeys = new HashSet<>();

    private StagedGlossary(final GlossaryRepository stored) {
        this.stored = stored;
    }

    /**
     * Copies a project's stored glossary.
     *
     * @param stored the repository to copy from and to ask about earlier removals; never null
     * @param projectId the project whose entries are copied; never null
     * @return the copy, or the stored repository's error
     */
    static Result<StagedGlossary> copyOf(final GlossaryRepository stored, final String projectId) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(projectId, "projectId");
        return stored.all(projectId).map(entries -> {
            final StagedGlossary copy = new StagedGlossary(stored);
            entries.forEach(entry -> copy.byId.put(entry.id(), entry));
            log.debug("Staged glossary project={} copied {} entries", projectId, entries.size());
            return copy;
        });
    }

    @Override
    public Result<GlossaryEntry> add(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        final String key = GlossaryKeys.of(entry.term());
        if (find(key).isPresent()) {
            log.debug("Staged glossary add refused as duplicate entryId={}", entry.id());
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Duplicate glossary term",
                    "That term already exists in this project's glossary, ignoring case."));
        }
        byId.put(entry.id(), entry);
        removedKeys.remove(key);
        log.debug("Staged glossary add entryId={}", entry.id());
        return Result.ok(entry);
    }

    @Override
    public Result<GlossaryEntry> update(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!byId.containsKey(entry.id())) {
            log.debug("Staged glossary update refused as unknown entryId={}", entry.id());
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Unknown glossary entry",
                    "No glossary entry '" + entry.id() + "' exists in this project's glossary."));
        }
        byId.put(entry.id(), entry);
        log.debug("Staged glossary update entryId={}", entry.id());
        return Result.ok(entry);
    }

    @Override
    public Result<Optional<GlossaryEntry>> update(
            final String projectId, final String term, final UnaryOperator<GlossaryEntry> change) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(change, "change");
        final Optional<GlossaryEntry> changed = find(GlossaryKeys.of(term)).map(held -> {
            final GlossaryEntry next = change.apply(held);
            byId.put(held.id(), next);
            return next;
        });
        log.debug("Staged glossary atomic update projectId={} present={}", projectId, changed.isPresent());
        return Result.ok(changed);
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        final GlossaryEntry removed = byId.remove(entryId);
        if (removed != null) {
            removedKeys.add(GlossaryKeys.of(removed.term()));
        }
        log.debug("Staged glossary remove projectId={} entryId={} removed={}", projectId, entryId, removed != null);
        return Result.ok(removed != null);
    }

    @Override
    public Result<List<GlossaryEntry>> all(final String projectId) {
        return Result.ok(entries());
    }

    /**
     * The copy's entries as they stand, in insertion order.
     *
     * @return the entries; never null, empty when there are none
     */
    List<GlossaryEntry> entries() {
        return List.copyOf(byId.values());
    }

    @Override
    public Result<Optional<GlossaryEntry>> findByTerm(final String projectId, final String term) {
        Objects.requireNonNull(term, "term");
        return Result.ok(find(GlossaryKeys.of(term)));
    }

    @Override
    public Result<Boolean> wasRemoved(final String projectId, final String term) {
        Objects.requireNonNull(term, "term");
        if (removedKeys.contains(GlossaryKeys.of(term))) {
            return Result.ok(true);
        }
        return stored.wasRemoved(projectId, term);
    }

    private Optional<GlossaryEntry> find(final String key) {
        return byId.values().stream()
                .filter(entry -> GlossaryKeys.of(entry.term()).equals(key))
                .findFirst();
    }
}
