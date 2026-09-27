package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.List;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * Reads and edits a project's glossary and runs the deterministic and model-assisted scans that propose new terms,
 * the seam the Names &amp; style screen calls.
 */
public interface GlossaryService {

    /**
     * Lists a project's glossary entries.
     *
     * @param projectId the non-null project id
     * @return the entries; never null, empty when the project has none
     */
    Result<List<GlossaryEntry>> entries(String projectId);

    /**
     * Runs the deterministic frequency scan over the project's book and adds any newly proposed term.
     *
     * @param projectId the non-null project id
     * @return the entries the scan added
     */
    Result<List<GlossaryEntry>> scan(String projectId);

    /**
     * Runs a model-assisted pre-scan over the project's book and adds any newly proposed term.
     *
     * @param projectId the non-null project id
     * @param model the non-null model to call
     * @return the entries the pre-scan added
     */
    Result<List<GlossaryEntry>> prescan(String projectId, ChatModel model);

    /**
     * Adds a glossary entry.
     *
     * @param entry the non-null entry to add
     * @return the added entry, or {@code validation} when the term already exists, ignoring case
     */
    Result<GlossaryEntry> add(GlossaryEntry entry);

    /**
     * Updates an existing glossary entry.
     *
     * @param entry the non-null replacement entry, matched by id
     * @return the updated entry, or {@code validation} when no entry holds that id
     */
    Result<GlossaryEntry> update(GlossaryEntry entry);

    /**
     * Removes a glossary entry.
     *
     * @param projectId the non-null project id
     * @param entryId the non-null entry id
     * @return {@code true} when an entry was removed, {@code false} when no entry held that id
     */
    Result<Boolean> remove(String projectId, String entryId);

    /**
     * Imports glossary entries from an RFC-4180 CSV file, matching an existing entry by term ignoring case.
     *
     * @param projectId the non-null project id
     * @param source the non-null CSV file to read
     * @return the number of rows imported
     */
    Result<Integer> importCsv(String projectId, Path source);

    /**
     * Exports the project's glossary to an RFC-4180 CSV file.
     *
     * @param projectId the non-null project id
     * @param destination the non-null CSV file to write
     * @return the written file's path
     */
    Result<Path> exportCsv(String projectId, Path destination);
}
