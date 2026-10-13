package ua.bookloom.pipeline.glossary;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;

/**
 * A private, in-memory copy of one project's recurring terms that the names pass edits instead of the stored list
 * (15i.N8), the counterpart of {@link StagedGlossary}: entries by {@link LexiconEntry#keyOf} in key order, a removal
 * remembered, and an answer for a term the person removed earlier from the stored lexicon. Used from the pass's
 * calling thread only; the project id of every call is that of the copied project.
 */
@Slf4j
final class StagedLexicon implements LexiconRepository {

    private final LexiconRepository stored;
    private final TreeMap<String, LexiconEntry> byKey = new TreeMap<>();
    private final Set<String> removedKeys = new HashSet<>();

    private StagedLexicon(final LexiconRepository stored) {
        this.stored = stored;
    }

    /**
     * Copies a project's stored recurring terms.
     *
     * @param stored the repository to copy from and to ask about earlier removals; never null
     * @param projectId the project whose entries are copied; never null
     * @return the copy, or the stored repository's error
     */
    static Result<StagedLexicon> copyOf(final LexiconRepository stored, final String projectId) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(projectId, "projectId");
        return stored.all(projectId).map(entries -> {
            final StagedLexicon copy = new StagedLexicon(stored);
            entries.forEach(entry -> copy.byKey.put(LexiconEntry.keyOf(entry.term()), entry));
            log.debug("Staged lexicon project={} copied {} entries", projectId, entries.size());
            return copy;
        });
    }

    @Override
    public Result<List<LexiconEntry>> all(final String projectId) {
        return Result.ok(entries());
    }

    /**
     * The copy's entries as they stand, in key order.
     *
     * @return the entries; never null, empty when there are none
     */
    List<LexiconEntry> entries() {
        return List.copyOf(byKey.values());
    }

    @Override
    public Result<LexiconEntry> put(final LexiconEntry entry) {
        Objects.requireNonNull(entry, "entry");
        final String key = LexiconEntry.keyOf(entry.term());
        byKey.put(key, entry);
        removedKeys.remove(key);
        log.debug("Staged lexicon put projectId={}", entry.projectId());
        return Result.ok(entry);
    }

    @Override
    public Result<Optional<LexiconEntry>> update(
            final String projectId, final String term, final UnaryOperator<LexiconEntry> change) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(change, "change");
        final String key = LexiconEntry.keyOf(term);
        final LexiconEntry held = byKey.get(key);
        final Optional<LexiconEntry> changed = Optional.ofNullable(held).map(change);
        changed.ifPresent(next -> byKey.put(key, next));
        log.debug("Staged lexicon update projectId={} present={}", projectId, changed.isPresent());
        return Result.ok(changed);
    }

    @Override
    public Result<LexiconEntry> record(final String projectId, final String term, final String rendering) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        final String key = LexiconEntry.keyOf(term);
        final LexiconEntry next =
                byKey.getOrDefault(key, LexiconEntry.of(projectId, term)).seen(rendering);
        byKey.put(key, next);
        removedKeys.remove(key);
        log.debug("Staged lexicon record projectId={}", projectId);
        return Result.ok(next);
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String term) {
        Objects.requireNonNull(term, "term");
        final String key = LexiconEntry.keyOf(term);
        final boolean removed = byKey.remove(key) != null;
        if (removed) {
            removedKeys.add(key);
        }
        log.debug("Staged lexicon remove projectId={} removed={}", projectId, removed);
        return Result.ok(removed);
    }

    @Override
    public Result<Boolean> wasRemoved(final String projectId, final String term) {
        Objects.requireNonNull(term, "term");
        if (removedKeys.contains(LexiconEntry.keyOf(term))) {
            return Result.ok(true);
        }
        return stored.wasRemoved(projectId, term);
    }
}
