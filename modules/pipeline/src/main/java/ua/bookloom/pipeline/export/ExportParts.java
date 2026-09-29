package ua.bookloom.pipeline.export;

import java.util.Objects;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.revision.ConsistencyPass;

/**
 * The collaborators one export job works with, handed over together by the export service.
 *
 * @param projects the stored projects
 * @param segments the stored segment records
 * @param openProjects the books open in this session
 * @param documents the reader and writer of book files
 * @param moves the move that puts each checked temporary file in place
 * @param consistencyPass the backward revision run before writing when asked
 * @param glossary the stored glossary the glossary side file is written from
 */
record ExportParts(
        ProjectRepository projects,
        SegmentRepository segments,
        OpenProjects openProjects,
        DocumentPort documents,
        ExportMoveOperation moves,
        ConsistencyPass consistencyPass,
        GlossaryRepository glossary) {

    /** Rejects a missing collaborator. */
    ExportParts {
        Objects.requireNonNull(projects, "projects");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(openProjects, "openProjects");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(moves, "moves");
        Objects.requireNonNull(consistencyPass, "consistencyPass");
        Objects.requireNonNull(glossary, "glossary");
    }
}
