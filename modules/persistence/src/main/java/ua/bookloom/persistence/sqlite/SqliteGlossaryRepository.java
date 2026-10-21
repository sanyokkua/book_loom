package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Stores a project's glossary as one {@link GlossaryDocument} in {@code project_docs}. Every change reads, changes
 * and writes the document inside one write transaction on the single writer, so a person's edit and a run's addition
 * made at the same moment both survive ({@code design.md} D9). Terms are matched by their {@link GlossaryKeys} key,
 * and removed terms are kept with the document, so they are still remembered after a restart.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteGlossaryRepository implements GlossaryRepository {

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<GlossaryEntry> add(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return db.write("glossary add", h -> addIn(h, entry)).flatMap(Function.identity());
    }

    private Result<GlossaryEntry> addIn(final Handle h, final GlossaryEntry entry) {
        final GlossaryDocument doc = load(h, codec, entry.projectId());
        if (doc.findByKey(GlossaryKeys.of(entry.term())).isPresent()) {
            log.debug(
                    "Glossary add refused as duplicate projectId={} termLength={}",
                    entry.projectId(),
                    entry.term().length());
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Duplicate glossary term",
                    "That term already exists in this project's glossary, ignoring case."));
        }
        store(h, codec, entry.projectId(), doc.added(entry));
        log.debug("Glossary term added projectId={} entryId={}", entry.projectId(), entry.id());
        return Result.ok(entry);
    }

    @Override
    public Result<GlossaryEntry> update(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return db.write("glossary update", h -> replaceIn(h, entry)).flatMap(Function.identity());
    }

    private Result<GlossaryEntry> replaceIn(final Handle h, final GlossaryEntry entry) {
        final GlossaryDocument doc = load(h, codec, entry.projectId());
        if (doc.findById(entry.id()).isEmpty()) {
            log.debug("Glossary update refused as unknown projectId={} entryId={}", entry.projectId(), entry.id());
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Unknown glossary entry",
                    "No glossary entry '" + entry.id() + "' exists in project '" + entry.projectId() + "'."));
        }
        store(h, codec, entry.projectId(), doc.replaced(entry));
        log.debug("Glossary term replaced projectId={} entryId={}", entry.projectId(), entry.id());
        return Result.ok(entry);
    }

    @Override
    public Result<Optional<GlossaryEntry>> update(
            final String projectId, final String term, final UnaryOperator<GlossaryEntry> change) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(change, "change");
        return db.write("glossary updateByTerm", h -> {
            final GlossaryDocument doc = load(h, codec, projectId);
            final Optional<GlossaryEntry> changed =
                    doc.findByKey(GlossaryKeys.of(term)).map(change);
            changed.ifPresent(entry -> store(h, codec, projectId, doc.replaced(entry)));
            log.debug("Glossary atomic update projectId={} present={}", projectId, changed.isPresent());
            return changed;
        });
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String entryId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(entryId, "entryId");
        return db.write("glossary remove", h -> {
            final GlossaryDocument doc = load(h, codec, projectId);
            final Optional<GlossaryEntry> removed = doc.findById(entryId);
            removed.ifPresent(entry -> store(h, codec, projectId, doc.without(entry)));
            log.debug("Glossary remove projectId={} entryId={} removed={}", projectId, entryId, removed.isPresent());
            return removed.isPresent();
        });
    }

    @Override
    public Result<List<GlossaryEntry>> all(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return db.read("glossary all", h -> {
            final List<GlossaryEntry> entries = load(h, codec, projectId).entries();
            log.debug("Glossary all projectId={} count={}", projectId, entries.size());
            return entries;
        });
    }

    @Override
    public Result<Optional<GlossaryEntry>> findByTerm(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        log.debug("Glossary findByTerm projectId={} termLength={}", projectId, term.length());
        return db.read("glossary findByTerm", h -> load(h, codec, projectId).findByKey(GlossaryKeys.of(term)));
    }

    @Override
    public Result<Boolean> wasRemoved(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        log.debug("Glossary wasRemoved projectId={} termLength={}", projectId, term.length());
        return db.read("glossary wasRemoved", h -> load(h, codec, projectId).wasRemoved(GlossaryKeys.of(term)));
    }

    /** Reads a project's glossary, empty when it has none; the checkpoint shares it. */
    static GlossaryDocument load(final Handle h, final JsonCodec codec, final String projectId) {
        return ProjectDocs.read(h, codec, projectId, DocKind.GLOSSARY, GlossaryDocument.class)
                .orElseGet(GlossaryDocument::empty);
    }

    /** Writes a project's glossary; the checkpoint shares it. */
    static void store(final Handle h, final JsonCodec codec, final String projectId, final GlossaryDocument doc) {
        ProjectDocs.write(h, codec, projectId, DocKind.GLOSSARY, doc);
    }
}
