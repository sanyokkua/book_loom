package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.ContextSnapshot;

/**
 * The one line that tells a reviewer what the model knew when it drafted a segment. Its words are the fixed
 * vocabulary of the context package, not prose, so they are not translated.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContextLine {

    private static final String SEPARATOR = " · ";

    /**
     * Names the parts of a context snapshot that were present.
     *
     * @param snapshot what a draft's context saw; never null
     * @return the present parts joined by a middle dot, or an empty string when the draft saw none of them
     */
    public static String of(final ContextSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        final List<String> parts = new ArrayList<>();
        if (!snapshot.styleSheet().isBlank()) {
            parts.add("brief");
        }
        if (!snapshot.glossary().isEmpty()) {
            parts.add("glossary(" + snapshot.glossary().size() + ")");
        }
        if (!snapshot.tmHits().isEmpty()) {
            parts.add("memory(" + snapshot.tmHits().size() + ")");
        }
        if (!snapshot.precedingTargets().isEmpty()) {
            parts.add("previous paragraph");
        }
        if (snapshot.summary() != null) {
            parts.add("summary");
        }
        log.debug("context line names {} parts", parts.size());
        return String.join(SEPARATOR, parts);
    }
}
