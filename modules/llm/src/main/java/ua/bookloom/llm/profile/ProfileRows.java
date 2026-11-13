package ua.bookloom.llm.profile;

import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelReport;

/**
 * Where the built-in rows come from: given who a model is and what its server says, the one row that handles it and how
 * it was matched. Until the built-in rows exist no row matches, so every model gets the default profile.
 */
@FunctionalInterface
public interface ProfileRows {

    /** No rows: every model gets the default profile. */
    ProfileRows NONE = (identity, facts) -> Optional.empty();

    /**
     * Picks the row that handles a model.
     *
     * @param identity who the model is, as its name and its server's metadata say; non-null
     * @param facts what its server said this session; non-null, unknown when nothing was read
     * @return the row and how it was matched, or empty when no row knows the model
     */
    Optional<Picked> pick(ModelIdentity identity, ModelFacts facts);

    /**
     * A row and how it was found.
     *
     * @param row the row; non-null
     * @param match how specific the match was; non-null, never {@link ModelReport.Match#OVERRIDE} or
     *     {@link ModelReport.Match#DEFAULT}
     */
    record Picked(ProfileRow row, ModelReport.Match match) {
        public Picked {
            Objects.requireNonNull(row, "row");
            Objects.requireNonNull(match, "match");
            if (match == ModelReport.Match.OVERRIDE || match == ModelReport.Match.DEFAULT) {
                throw new IllegalArgumentException("a row is never matched as " + match);
            }
        }
    }
}
