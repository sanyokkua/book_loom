package ua.bookloom.pipeline.export;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The collaborators one export job works with, handed over together by the export service.
 *
 * @param projects the stored projects
 * @param segments the stored segment records
 * @param openProjects the books open in this session
 * @param documents the reader and writer of book files
 * @param moves the move that puts each checked temporary file in place
 * @param deferrals the open deferrals the proofread summary counts as waiting
 * @param glossary the stored glossary the glossary side file is written from
 * @param runs the run history the report names the last run from
 * @param lexicon the recurring terms the proofread mark list reads
 */
record ExportParts(
        ProjectRepository projects,
        SegmentRepository segments,
        OpenProjects openProjects,
        DocumentPort documents,
        ExportMoveOperation moves,
        DeferralRepository deferrals,
        GlossaryRepository glossary,
        RunRepository runs,
        LexiconRepository lexicon) {

    /** Rejects a missing collaborator. */
    ExportParts {
        Objects.requireNonNull(projects, "projects");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(openProjects, "openProjects");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(moves, "moves");
        Objects.requireNonNull(deferrals, "deferrals");
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(lexicon, "lexicon");
    }

    /** The project's last run, which the report names; null when it never ran or the history cannot be read. */
    @Nullable
    RunRecord lastRun(final String projectId) {
        final Result<Optional<RunRecord>> latest = runs.latest(projectId);
        return latest.isErr() ? null : Objects.requireNonNull(latest.data()).orElse(null);
    }
}
