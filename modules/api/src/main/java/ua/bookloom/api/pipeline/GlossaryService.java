package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
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
     * Lists the characters whose gender is still unknown and whom the book names often enough to be worth asking about.
     *
     * @param projectId the non-null project id
     * @param minMentions how many times the body text must name a character for it to be listed; at least one
     * @return the characters, most mentioned first; never null, empty when none qualifies or the glossary is empty
     */
    Result<List<UnknownGender>> unknownGenders(String projectId, int minMentions);

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
     * @param progress the non-null receiver of each model call's start and finish, on the calling thread
     * @return the entries the pre-scan added; nothing is added unless every call answered
     */
    Result<List<GlossaryEntry>> prescan(String projectId, ChatModel model, Consumer<JobEvent> progress);

    /**
     * Asks the model whether each unlocked term with no target is a name, a term or not a name: a term judged not a
     * name is removed (and remembered as removed) when its type and gender were never set, and a type or gender still
     * at {@code OTHER}/{@code UNKNOWN} takes the model's guess. A locked term or one with a target is never touched.
     *
     * @param projectId the non-null project id
     * @param model the non-null model to call
     * @param progress the non-null receiver of each model call's start and finish, on the calling thread
     * @return what was removed and updated and the glossary as it now stands; nothing changes unless every call
     *     answered
     */
    Result<GlossaryReviewReport> review(String projectId, ChatModel model, Consumer<JobEvent> progress);

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
     * @return what was imported and which lines were malformed or refused, or {@code validation} when the file cannot
     *     be read
     */
    Result<GlossaryImportReport> importCsv(String projectId, Path source);

    /**
     * Exports the project's glossary to an RFC-4180 CSV file.
     *
     * @param projectId the non-null project id
     * @param destination the non-null CSV file to write
     * @return the written file's path
     */
    Result<Path> exportCsv(String projectId, Path destination);
}
