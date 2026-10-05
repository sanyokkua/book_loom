package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Stores a project's recurring-term lexicon over the shared {@link InMemoryStore}, one entry per term compared by
 * {@link LexiconEntry#keyOf}; {@link #record} counts through one atomic map step so concurrent drafts never lose a use.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryLexiconRepository implements LexiconRepository {

    private final InMemoryStore store;

    @Override
    public Result<List<LexiconEntry>> all(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            final List<LexiconEntry> entries = store.lexicon(projectId).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .map(Map.Entry::getValue)
                    .toList();
            log.trace("Lexicon all projectId={} entries={}", projectId, entries.size());
            return Result.ok(entries);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<LexiconEntry> put(final LexiconEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            store.lexicon(entry.projectId()).put(LexiconEntry.keyOf(entry.term()), entry);
            log.debug("Lexicon put projectId={} renderings={}", entry.projectId(), entry.distinctRenderings());
            log.trace(
                    "Lexicon put term={} established={}",
                    entry.term(),
                    entry.established().orElse(null));
            return Result.ok(entry);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<LexiconEntry> record(final String projectId, final String term, final String rendering) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        try {
            final LexiconEntry stored = store.lexicon(projectId)
                    .compute(
                            LexiconEntry.keyOf(term),
                            (key, held) -> (held == null ? LexiconEntry.of(projectId, term) : held).seen(rendering));
            log.debug("Lexicon record projectId={} distinct={}", projectId, stored.distinctRenderings());
            log.trace("Lexicon record term={} rendering={}", term, rendering);
            return Result.ok(stored);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        try {
            final boolean removed = store.lexicon(projectId).remove(LexiconEntry.keyOf(term)) != null;
            log.debug("Lexicon remove projectId={} removed={}", projectId, removed);
            return Result.ok(removed);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Lexicon storage failure", "The recurring terms could not be stored.", null, cause);
        log.error("Unexpected lexicon repository failure code={}", error.code(), cause);
        return error;
    }
}
