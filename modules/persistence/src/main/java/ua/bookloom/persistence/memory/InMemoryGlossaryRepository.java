package ua.bookloom.persistence.memory;

import static ua.bookloom.util.contract.ArgNames.PROJECT_ID;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.persistence.GlossaryIndex;
import ua.bookloom.persistence.memory.InMemoryStore.ProjectGlossary;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Stores a project's glossary over the shared {@link InMemoryStore}, terms matched by their {@link GlossaryKeys} key over
 * every lemma and form ({@link GlossaryIndex}) and a
 * person's removal remembered so a re-proposed name is not silently re-added.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryGlossaryRepository implements GlossaryRepository {

    private final InMemoryStore store;

    @Override
    public Result<GlossaryEntry> add(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            final ProjectGlossary glossary = store.glossary(entry.projectId());
            if (glossary.holderOf(entry).isPresent()) {
                log.debug(
                        "Glossary add refused as duplicate projectId={} termLength={}",
                        entry.projectId(),
                        entry.term().length());
                return Result.err(AppError.of(
                        ErrorCode.validation,
                        "Duplicate glossary term",
                        "That term already exists in this project's glossary, ignoring case."));
            }
            glossary.put(entry);
            GlossaryIndex.keysOf(entry).forEach(glossary.removedKeys()::remove);
            log.debug(
                    "Glossary term added projectId={} entryId={} termLength={}",
                    entry.projectId(),
                    entry.id(),
                    entry.term().length());
            return Result.ok(entry);
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<GlossaryEntry> update(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            final ProjectGlossary glossary = store.glossary(entry.projectId());
            if (!glossary.byId().containsKey(entry.id())) {
                log.debug("Glossary update refused as unknown projectId={} entryId={}", entry.projectId(), entry.id());
                return Result.err(unknownEntry(entry.projectId(), entry.id()));
            }
            glossary.put(entry);
            log.debug("Glossary term replaced projectId={} entryId={}", entry.projectId(), entry.id());
            return Result.ok(entry);
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<GlossaryEntry>> update(
            final String projectId, final String term, final UnaryOperator<GlossaryEntry> change) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(change, "change");
        try {
            final Optional<GlossaryEntry> stored = store.glossary(projectId).update(GlossaryKeys.of(term), change);
            log.debug("Glossary atomic update projectId={} present={}", projectId, stored.isPresent());
            return Result.ok(stored);
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String entryId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(entryId, "entryId");
        try {
            final ProjectGlossary glossary = store.glossary(projectId);
            final Optional<GlossaryEntry> removed = glossary.removeById(entryId);
            removed.ifPresent(entry -> glossary.removedKeys().addAll(GlossaryIndex.keysOf(entry)));
            log.debug("Glossary remove projectId={} entryId={} removed={}", projectId, entryId, removed.isPresent());
            return Result.ok(removed.isPresent());
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<GlossaryEntry> fold(final String projectId, final String foldedId, final GlossaryEntry kept) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(foldedId, "foldedId");
        Objects.requireNonNull(kept, "kept");
        try {
            final ProjectGlossary glossary = store.glossary(projectId);
            if (!glossary.byId().containsKey(foldedId) || !glossary.byId().containsKey(kept.id())) {
                log.debug("Glossary fold refused projectId={}: an entry is not held", projectId);
                return Result.err(unknownEntry(projectId, foldedId));
            }
            glossary.removeById(foldedId);
            glossary.put(kept);
            log.debug("Glossary fold projectId={} foldedId={} keptId={}", projectId, foldedId, kept.id());
            return Result.ok(kept);
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<GlossaryEntry>> all(final String projectId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        try {
            return Result.ok(store.glossary(projectId).allInInsertionOrder());
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<GlossaryEntry>> findByTerm(final String projectId, final String term) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(term, "term");
        try {
            return Result.ok(store.glossary(projectId).findByKey(GlossaryKeys.of(term)));
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> wasRemoved(final String projectId, final String term) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(term, "term");
        try {
            return Result.ok(store.glossary(projectId).removedKeys().contains(GlossaryKeys.of(term)));
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError unknownEntry(final String projectId, final String entryId) {
        return AppError.of(
                ErrorCode.validation,
                "Unknown glossary entry",
                "No glossary entry '" + entryId + "' exists in project '" + projectId + "'.");
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Glossary storage failure", "The glossary entry could not be stored.", null, cause);
        log.error("Unexpected glossary repository failure code={}", error.code(), cause);
        return error;
    }
}
