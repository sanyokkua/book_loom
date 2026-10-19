package ua.bookloom.pipeline.glossary;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.util.text.GlossaryKeys;

/** The one rule for a glossary entry's id, so every scan and every merge names the same term the same way. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlossaryIds {

    /**
     * The id of a project's entry for a term.
     *
     * @param projectId the owning project's id; never null
     * @param term the source term; never null, compared by its {@link GlossaryKeys} key
     * @return {@code projectId:} followed by the term's key
     */
    public static String of(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        return projectId + ":" + GlossaryKeys.of(term);
    }
}
