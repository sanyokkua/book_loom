package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Reads and edits a project's recurring-term lexicon and proposes its terms, the seam the Recurring terms section of
 * Names &amp; style calls.
 */
public interface LexiconService {

    /**
     * Lists a project's lexicon.
     *
     * @param projectId the non-null project id
     * @return the entries; never null, empty when the project has none
     */
    Result<List<LexiconEntry>> entries(String projectId);

    /**
     * Runs the deterministic scan for recurring common terms and titles over the project's book and adds each new one.
     *
     * @param projectId the non-null project id
     * @return the lexicon as it now stands
     */
    Result<List<LexiconEntry>> scan(String projectId);

    /**
     * Asks the model for a rendering of each term that has none yet, by the same call the glossary's suggestions use;
     * nothing is written unless every request answered.
     *
     * @param projectId the non-null project id
     * @param model the non-null model to call
     * @param progress the non-null receiver of each call's start and finish, on the calling thread
     * @return the lexicon as it now stands
     */
    Result<List<LexiconEntry>> suggest(String projectId, ChatModel model, Consumer<JobEvent> progress);

    /**
     * Adds a term by hand.
     *
     * @param projectId the non-null project id
     * @param term the non-blank source term
     * @return the added entry, or {@code validation} when the term is blank or already held, in the lexicon or the
     *     glossary
     */
    Result<LexiconEntry> add(String projectId, String term);

    /**
     * Sets the rendering the person wants kept.
     *
     * @param projectId the non-null project id
     * @param term the non-null term of a held entry
     * @param rendering the rendering, or null or blank to clear the choice
     * @return the entry as it now stands, or {@code validation} when no entry holds the term
     */
    Result<LexiconEntry> edit(String projectId, String term, @Nullable String rendering);

    /**
     * Moves an entry to the glossary with its established rendering as the target, so the rendering becomes the
     * person's and is applied exactly; the lexicon no longer holds the term.
     *
     * @param projectId the non-null project id
     * @param term the non-null term of a held entry
     * @return the new glossary entry, or {@code validation} when no entry holds the term or the glossary has it
     */
    Result<GlossaryEntry> promote(String projectId, String term);

    /**
     * Removes a term from the lexicon.
     *
     * @param projectId the non-null project id
     * @param term the non-null term
     * @return {@code true} when an entry was removed, {@code false} when none held the term
     */
    Result<Boolean> remove(String projectId, String term);
}
