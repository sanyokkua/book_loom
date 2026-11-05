package ua.bookloom.api.persistence;

import java.util.List;
import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.PromptFrame;

/**
 * Stores each distinct fixed part of a project's prompts once ({@code specs/local-storage/spec.md} "Store each distinct
 * fixed part of a run's prompts once per project"). Removing the project removes its frames.
 */
public interface PromptFrameRepository {

    /**
     * Stores a frame under its hash; saving a frame the project already holds changes nothing.
     *
     * @param projectId the non-null id of a stored project
     * @param frame the non-null frame
     * @return the frame as stored
     */
    Result<PromptFrame> save(String projectId, PromptFrame frame);

    /**
     * Finds one frame of a project.
     *
     * @param projectId the non-null project id
     * @param hash the non-null hash a segment's snapshot names
     * @return the frame if the project holds it, or empty if not
     */
    Result<Optional<PromptFrame>> find(String projectId, String hash);

    /**
     * Lists a project's frames.
     *
     * @param projectId the non-null project id
     * @return never null; empty if the project holds none, ordered by hash
     */
    Result<List<PromptFrame>> all(String projectId);
}
