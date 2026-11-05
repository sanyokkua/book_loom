package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.PromptFrameRepository;
import ua.bookloom.api.project.PromptFrame;

/** Keeps each project's prompt frames by hash over the shared {@link InMemoryStore}, forgotten with the project. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryPromptFrameRepository implements PromptFrameRepository {

    private static final String PROJECT_ID = "projectId";

    private final InMemoryStore store;

    @Override
    public Result<PromptFrame> save(final String projectId, final PromptFrame frame) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(frame, "frame");
        try {
            final PromptFrame held = store.frames(projectId).putIfAbsent(frame.hash(), frame);
            log.debug("Prompt frame save projectId={} hash={} alreadyHeld={}", projectId, frame.hash(), held != null);
            return Result.ok(frame);
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<PromptFrame>> find(final String projectId, final String hash) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(hash, "hash");
        try {
            log.debug("Prompt frame find projectId={} hash={}", projectId, hash);
            return Result.ok(Optional.ofNullable(store.frames(projectId).get(hash)));
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<PromptFrame>> all(final String projectId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        try {
            log.debug("Prompt frame all projectId={}", projectId);
            return Result.ok(store.frames(projectId).values().stream()
                    .sorted(Comparator.comparing(PromptFrame::hash))
                    .toList());
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "Prompt frame storage failure",
                "The prompt frame could not be stored.",
                null,
                cause);
        log.error("Unexpected prompt frame repository failure code={}", error.code(), cause);
        return error;
    }
}
