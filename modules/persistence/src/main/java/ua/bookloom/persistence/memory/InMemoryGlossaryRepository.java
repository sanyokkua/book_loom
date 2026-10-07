package ua.bookloom.persistence.memory;

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
import ua.bookloom.persistence.memory.InMemoryStore.ProjectGlossary;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Stores a project's glossary over the shared {@link InMemoryStore}, terms matched by their {@link GlossaryKeys} key and a
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
            final String key = GlossaryKeys.of(entry.term());
            if (glossary.findByKey(key).isPresent()) {
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
            glossary.removedKeys().remove(key);
            log.debug(
                    "Glossary term added projectId={} entryId={} termLength={}",
                    entry.projectId(),
                    entry.id(),
                    entry.term().length());
            return Result.ok(entry);
        } catch (Throwable cause) {
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
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<GlossaryEntry>> update(
            final String projectId, final String term, final UnaryOperator<GlossaryEntry> change) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(change, "change");
        try {
            final Optional<GlossaryEntry> stored = store.glossary(projectId).update(GlossaryKeys.of(term), change);
            log.debug("Glossary atomic update projectId={} present={}", projectId, stored.isPresent());
            return Result.ok(stored);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String entryId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(entryId, "entryId");
        try {
            final ProjectGlossary glossary = store.glossary(projectId);
            final Optional<GlossaryEntry> removed = glossary.removeById(entryId);
            removed.ifPresent(entry -> glossary.removedKeys().add(GlossaryKeys.of(entry.term())));
            log.debug("Glossary remove projectId={} entryId={} removed={}", projectId, entryId, removed.isPresent());
            return Result.ok(removed.isPresent());
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<GlossaryEntry>> all(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return Result.ok(store.glossary(projectId).allInInsertionOrder());
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<GlossaryEntry>> findByTerm(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        try {
            return Result.ok(store.glossary(projectId).findByKey(GlossaryKeys.of(term)));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> wasRemoved(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        try {
            return Result.ok(store.glossary(projectId).removedKeys().contains(GlossaryKeys.of(term)));
        } catch (Throwable cause) {
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
