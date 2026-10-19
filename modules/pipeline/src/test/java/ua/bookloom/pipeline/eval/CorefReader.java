package ua.bookloom.pipeline.eval;

import java.util.List;

/**
 * The seam of the coreference probe: what a model says about one term in one window. 15i.N4's term reading plugs in
 * here, so the scorer does not know a prompt or a model.
 */
@FunctionalInterface
interface CorefReader {

    /**
     * Reads one window.
     *
     * @param term the name asked about
     * @param windowText the window's paragraphs, joined
     * @return the pronouns the model says refer to the term, quoted; never null, empty when it answers "none"
     */
    List<CorefClaim> read(String term, String windowText);
}
