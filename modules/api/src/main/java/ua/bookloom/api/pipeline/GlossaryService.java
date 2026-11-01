package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;

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
     * Lists the characters whose gender is unknown or only suggested (each says which) and whom the book names often enough to be worth asking about.
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
     * @return the entries the scan added; nothing when the glossary already held entries
     */
    Result<EntryChanges<GlossaryEntry>> scan(String projectId);

    /**
     * Runs a model-assisted pre-scan over the project's book and adds any newly proposed term.
     *
     * @param projectId the non-null project id
     * @param model the non-null model to call
     * @param progress the non-null receiver of each model call's start and finish, on the calling thread
     * @return the entries the pre-scan added; nothing is added unless every call answered, and nothing is asked below
     *     the Max dial when the code already proposes ten names or more
     */
    Result<EntryChanges<GlossaryEntry>> prescan(String projectId, ChatModel model, Consumer<JobEvent> progress);

    /**
     * Asks the model whether each unlocked term with no target is a name, a term or not a name: a term judged not a
     * name is removed (and remembered as removed) when its type and gender were never set, and a type or gender still
     * at {@code OTHER}/{@code UNKNOWN} takes the model's guess. A locked term or one with a target is never touched.
     *
     * @param projectId the non-null project id
     * @param model the non-null model to call
     * @param progress the non-null receiver of each model call's start and finish, on the calling thread
     * @return what was removed and updated, the glossary as it now stands and the exact changes with the model's
     *     reason; nothing changes unless every call answered
     */
    Result<GlossaryReviewReport> review(String projectId, ChatModel model, Consumer<JobEvent> progress);

    /**
     * Asks the model for a target of each unlocked term that has none or only an earlier suggestion, and nothing else:
     * no verdict on whether a term is a name, no type or gender change. A target the person chose is never touched.
     * The rendering follows the Book Brief's name policy.
     *
     * @param projectId the non-null project id
     * @param model the non-null model to call
     * @param progress the non-null receiver of each model call's start and finish, on the calling thread
     * @return the entries that took a suggested target, each as a change from its state before; nothing is written
     *     unless every call answered
     */
    Result<EntryChanges<GlossaryEntry>> translate(String projectId, ChatModel model, Consumer<JobEvent> progress);

    /**
     * Accepts every suggested target that passes the target language's checks: an inflecting target is pinned, a
     * target that never inflects (an identifier, a kept original, a checked name where names are not declined) is
     * locked. A suggestion that fails a check or carries a review flag stays suggested; an entry with no target, a
     * pinned one and a locked one are never touched.
     *
     * @param projectId the non-null project id
     * @return the accepted entries and the rejected ones with their reasons
     */
    Result<AcceptReport> acceptSuggested(String projectId);

    /**
     * The review flags of the targets the glossary holds (an oblique-looking target, a word count unlike the term's),
     * computed on each call from the target language's file and never stored, so a changed target or file changes them
     * at once.
     *
     * @param projectId the non-null project id
     * @return the flags per entry id, only for the entries that carry one; never null, empty when none is flagged or no
     *     target language is chosen
     */
    Result<Map<String, List<TargetIssue>>> reviewFlags(String projectId);

    /**
     * The names the names pass marked for the once-per-name question about a meaning the book plays on, still
     * unanswered ({@link MeaningChoice#PENDING}); the question at Start translation lists them.
     *
     * @param projectId the non-null project id
     * @return the entries in glossary order; never null, empty when none waits
     */
    Result<List<GlossaryEntry>> meaningQuestions(String projectId);

    /**
     * Stores the person's answers to the once-per-name question about names whose meaning the book plays on (design
     * D5). Every answer keeps the target, so a run that starts at once still renders the name; translate leaves an
     * earlier suggestion for the next suggestion call to replace by asking for the name by its meaning;
     * {@link MeaningChoice#PENDING}, the question closed unanswered, is
     * stored as transcribe. No model is called.
     *
     * @param projectId the non-null project id
     * @param answers the non-null answer per entry id; an id the glossary no longer holds is skipped
     * @return the entries the answers changed, as stored
     */
    Result<List<GlossaryEntry>> answerMeaning(String projectId, Map<String, MeaningChoice> answers);

    /**
     * Stores the genders the person confirmed or changed in the Start question about characters nobody settled
     * (glossary "Ask at Start for the genders nobody has settled"): each answer becomes the person's value, even one
     * that only confirms the offered rule's or likely value. A row the person left alone is not in the map and stays a
     * hint. No model is called.
     *
     * @param projectId the non-null project id
     * @param answers the non-null gender per entry id; an id the glossary no longer holds is skipped
     * @return the entries the answers changed, as stored
     */
    Result<List<GlossaryEntry>> answerGenders(String projectId, Map<String, Gender> answers);

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
