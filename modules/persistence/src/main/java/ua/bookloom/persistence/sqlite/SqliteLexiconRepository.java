package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Stores a project's recurring-term lexicon as one {@link LexiconDocument} in {@code project_docs}. Every change, a
 * counted use included, reads, changes and writes the document inside one write transaction on the single writer, so
 * concurrent drafts and edits never lose a use ({@code design.md} D9); removed terms are kept with the document.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteLexiconRepository implements LexiconRepository {

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<List<LexiconEntry>> all(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return db.read("lexicon all", h -> {
            final List<LexiconEntry> entries = load(h, projectId).entries();
            log.debug("Lexicon all projectId={} entries={}", projectId, entries.size());
            return entries;
        });
    }

    @Override
    public Result<LexiconEntry> put(final LexiconEntry entry) {
        Objects.requireNonNull(entry, "entry");
        log.debug("Lexicon put projectId={} renderings={}", entry.projectId(), entry.distinctRenderings());
        log.trace("Lexicon put term={}", entry.term());
        return db.write("lexicon put", h -> {
            store(h, entry.projectId(), load(h, entry.projectId()).stored(entry));
            return entry;
        });
    }

    @Override
    public Result<Optional<LexiconEntry>> update(
            final String projectId, final String term, final UnaryOperator<LexiconEntry> change) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(change, "change");
        return db.write("lexicon update", h -> {
            final LexiconDocument doc = load(h, projectId);
            final Optional<LexiconEntry> changed =
                    doc.find(LexiconEntry.keyOf(term)).map(change);
            changed.ifPresent(entry -> store(h, projectId, doc.stored(entry)));
            log.debug("Lexicon update projectId={} present={}", projectId, changed.isPresent());
            return changed;
        });
    }

    @Override
    public Result<LexiconEntry> record(final String projectId, final String term, final String rendering) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        log.trace("Lexicon record term={} rendering={}", term, rendering);
        return db.write("lexicon record", h -> {
            final LexiconDocument doc = load(h, projectId);
            final LexiconEntry counted = doc.find(LexiconEntry.keyOf(term))
                    .orElseGet(() -> LexiconEntry.of(projectId, term))
                    .seen(rendering);
            store(h, projectId, doc.stored(counted));
            log.debug("Lexicon record projectId={} distinct={}", projectId, counted.distinctRenderings());
            return counted;
        });
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        return db.write("lexicon remove", h -> {
            final String key = LexiconEntry.keyOf(term);
            final LexiconDocument doc = load(h, projectId);
            final boolean removed = doc.find(key).isPresent();
            if (removed) {
                store(h, projectId, doc.without(key));
            }
            log.debug("Lexicon remove projectId={} removed={}", projectId, removed);
            return removed;
        });
    }

    @Override
    public Result<Boolean> wasRemoved(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        log.debug("Lexicon wasRemoved projectId={}", projectId);
        return db.read("lexicon wasRemoved", h -> load(h, projectId).wasRemoved(LexiconEntry.keyOf(term)));
    }

    private LexiconDocument load(final Handle h, final String projectId) {
        return ProjectDocs.read(h, codec, projectId, DocKind.LEXICON, LexiconDocument.class)
                .orElseGet(LexiconDocument::empty);
    }

    private void store(final Handle h, final String projectId, final LexiconDocument doc) {
        ProjectDocs.write(h, codec, projectId, DocKind.LEXICON, doc);
    }
}
