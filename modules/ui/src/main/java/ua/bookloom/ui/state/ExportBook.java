package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewDesk;

/**
 * The open book an export is written for.
 *
 * @param project the open project, whose book, brief and name the export follows
 * @param desk the review desk, whose open work the export reads
 * @param projects the stored projects
 * @param proofread where the Proofread stage stands, which the export screen warns about
 */
public record ExportBook(
        CurrentProject project, ReviewDesk desk, ProjectService projects, ProofreadStanding proofread) {

    /** Rejects a missing part. */
    @Inject
    public ExportBook {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(desk, "desk");
        Objects.requireNonNull(projects, "projects");
        Objects.requireNonNull(proofread, "proofread");
    }
}
