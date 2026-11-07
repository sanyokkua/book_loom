package ua.bookloom.pipeline.revision;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The Proofread stage's term sweep: every open TERM deferral is answered by code, with no model call, through the same
 * {@link TermSweep} the export's backward revision runs, so a renamed glossary target reaches the decided text at every
 * dial.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class CodeSweep {

    private final DocumentPort documents;
    private final OpenProjects openProjects;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;
    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;

    /**
     * What one sweep changed.
     *
     * @param substitutions how many term deferrals were swept into a machine target
     * @param proposals how many swept texts wait as a proposal on a segment the person edited
     */
    public record Swept(int substitutions, int proposals) {}

    /**
     * Sweeps every open TERM deferral of a project.
     *
     * @param projectId the non-null project id
     * @param document the non-null book the decided segments belong to, the run's own even if it was closed meanwhile
     * @return what the sweep changed; {@code validation} when the project is not stored or names no target language; a
     *     storage or gate error; {@code internal} for an unexpected failure
     */
    public Result<Swept> run(final String projectId, final Document document) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(document, "document");
        log.debug("Term sweep requested project={}", projectId);
        try {
            return new PassReader(documents, openProjects, projects, glossary, lexicon)
                    .read(projectId, document)
                    .flatMap(this::sweep);
        } catch (Exception cause) {
            log.error("Unexpected term sweep failure project={}", projectId, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Term sweep failed",
                    "An unexpected failure stopped the sweep of renamed glossary targets.",
                    null,
                    cause));
        }
    }

    private Result<Swept> sweep(final PassInputs inputs) {
        final Result<List<Deferral>> open = deferrals.open(inputs.projectId());
        if (open.isErr()) {
            return Result.err(Objects.requireNonNull(open.error(), ERROR));
        }
        final PassTally tally = new PassTally();
        return new TermSweep(glossary, segments, new RevisionWriter(segments, deferrals))
                .sweep(inputs, Objects.requireNonNull(open.data(), "open"), tally)
                .map(done -> {
                    final Swept swept = new Swept(tally.termSubstitutionCount(), tally.proposalCount());
                    log.debug(
                            "Term sweep done project={} substitutions={} proposals={}",
                            inputs.projectId(),
                            swept.substitutions(),
                            swept.proposals());
                    return swept;
                });
    }
}
