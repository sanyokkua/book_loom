package ua.bookloom.pipeline.eval;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Narrator;

/**
 * What a case adds around its text, which a real run always has and a bare prompt does not: the earlier pairs of the
 * chapter, the rolling summary, the recurring terms the run has learned and who narrates. A case file that leaves it
 * out gets none of them.
 *
 * @param preceding the unit's earlier source and masked target pairs, oldest first
 * @param summary the rolling summary's text, or null while there is none
 * @param lexicon the recurring terms with the rendering the run established for each
 * @param narrator the brief's narrator, or null when the brief does not say
 */
public record EvalContext(
        List<Pair> preceding,
        @Nullable String summary,
        List<Rendering> lexicon,
        @Nullable Narrator narrator) {

    private static final EvalContext NONE = new EvalContext(List.of(), null, List.of(), null);

    /** One earlier segment with the target the run kept for it: its protected names are already written out. */
    public record Pair(String source, String target) {}

    /** One recurring term and the rendering the lexicon established for it. */
    public record Rendering(String term, String rendering) {}

    /** Copies the lists; a case file may leave them out. */
    public EvalContext {
        preceding = preceding == null ? List.of() : List.copyOf(preceding);
        lexicon = lexicon == null ? List.of() : List.copyOf(lexicon);
    }

    public static EvalContext none() {
        return NONE;
    }
}
