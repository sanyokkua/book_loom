package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.PromptFrameRepository;
import ua.bookloom.api.project.PromptFrame;

/**
 * Stores each prompt frame as one {@code project_docs} document named {@code promptFrame:<hash>}; the projects
 * table's cascade removes them with their project.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqlitePromptFrameRepository implements PromptFrameRepository {

    private static final String PROJECT_ID = "projectId";

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<PromptFrame> save(final String projectId, final PromptFrame frame) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(frame, "frame");
        log.debug(
                "Prompt frame save projectId={} hash={} sections={}",
                projectId,
                frame.hash(),
                frame.sections().size());
        // The hash names the frame's text, so writing the same hash again writes the same document.
        return db.write("prompt frame save", h -> {
            ProjectDocs.write(h, codec, projectId, DocKind.PROMPT_FRAME, frame.hash(), frame);
            return frame;
        });
    }

    @Override
    public Result<Optional<PromptFrame>> find(final String projectId, final String hash) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(hash, "hash");
        log.debug("Prompt frame find projectId={} hash={}", projectId, hash);
        return db.read(
                "prompt frame find",
                h -> ProjectDocs.read(h, codec, projectId, DocKind.PROMPT_FRAME, hash, PromptFrame.class));
    }

    @Override
    public Result<List<PromptFrame>> all(final String projectId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        log.debug("Prompt frame all projectId={}", projectId);
        return db.read(
                "prompt frame all",
                h -> ProjectDocs.readAll(h, codec, projectId, DocKind.PROMPT_FRAME, PromptFrame.class));
    }
}
