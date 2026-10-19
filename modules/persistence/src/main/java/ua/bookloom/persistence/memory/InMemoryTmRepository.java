package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.TmEntry;

/**
 * Stores a project's translation memory over the shared {@link InMemoryStore}, keyed by source hash and context key
 * — a repeated {@link #put} for the same key replaces the prior entry.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryTmRepository implements TmRepository {

    private final InMemoryStore store;

    @Override
    public Result<TmEntry> put(final TmEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            store.tm(entry.projectId()).put(InMemoryStore.tmKey(entry.sourceHash(), entry.contextKey()), entry);
            log.debug(
                    "TM entry stored projectId={} entryId={} sourceHash={} contextKey={}",
                    entry.projectId(),
                    entry.id(),
                    entry.sourceHash(),
                    entry.contextKey());
            return Result.ok(entry);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<TmEntry>> exact(final String projectId, final String sourceHash) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceHash, "sourceHash");
        try {
            return Result.ok(store.tm(projectId).values().stream()
                    .filter(entry -> entry.sourceHash().equals(sourceHash))
                    .toList());
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<TmEntry>> context(final String projectId, final String sourceHash, final String contextKey) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(contextKey, "contextKey");
        try {
            return Result.ok(Optional.ofNullable(store.tm(projectId).get(InMemoryStore.tmKey(sourceHash, contextKey))));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<TmEntry>> candidates(final String projectId, final int minChars, final int maxChars) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return Result.ok(store.tm(projectId).values().stream()
                    .filter(entry -> {
                        final int length = entry.sourceInner()
                                .codePointCount(0, entry.sourceInner().length());
                        return length >= minChars && length <= maxChars;
                    })
                    .toList());
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "Translation-memory storage failure",
                "The TM entry could not be stored.",
                null,
                cause);
        log.error("Unexpected TM repository failure code={}", error.code(), cause);
        return error;
    }
}
