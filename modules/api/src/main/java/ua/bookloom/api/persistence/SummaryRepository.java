package ua.bookloom.api.persistence;

import java.util.List;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.ChapterSummary;

/**
 * Stores and reads a project's chapter summaries, one record per chapter
 * ({@code specs/local-storage/spec.md} "Store each chapter summary as its own record"). Removing the project removes
 * them, so the port has no remove of its own.
 */
public interface SummaryRepository {

    /**
     * Saves one chapter summary in one transaction, replacing the summary stored for the same project and chapter key;
     * a replaced summary keeps its place in {@link #list(String)}.
     *
     * @param summary the non-null summary to save
     * @return the saved summary
     */
    Result<ChapterSummary> save(ChapterSummary summary);

    /**
     * Lists a project's chapter summaries in the order they were first saved, oldest first.
     *
     * @param projectId the non-null project id
     * @return never null; empty when the project has none or is unknown
     */
    Result<List<ChapterSummary>> list(String projectId);
}
