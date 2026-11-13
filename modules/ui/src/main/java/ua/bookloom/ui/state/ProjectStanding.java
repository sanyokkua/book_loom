package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * What the run-state row reads about the open project beyond its run: the stored name the title bar shows and where
 * its Proofread stage stands, which a Continue line names. Grouped so {@link RunStateSource} follows them as one input.
 */
@Slf4j
public final class ProjectStanding {

    private final BriefProject briefProject;
    private final ProofreadStanding proofread;

    /**
     * Holds the two sources.
     *
     * @param briefProject the open project's stored name
     * @param proofread where the open project's Proofread stage stands
     */
    @Inject
    public ProjectStanding(final BriefProject briefProject, final ProofreadStanding proofread) {
        this.briefProject = Objects.requireNonNull(briefProject, "briefProject");
        this.proofread = Objects.requireNonNull(proofread, "proofread");
        log.debug("project standing wired");
    }

    BriefProject briefProject() {
        return briefProject;
    }

    ProofreadStanding proofread() {
        return proofread;
    }
}
