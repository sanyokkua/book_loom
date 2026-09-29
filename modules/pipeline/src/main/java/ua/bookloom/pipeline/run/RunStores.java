package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The places a run reads its project from and stores its decisions in.
 *
 * @param projects the stored projects
 * @param segments the stored segment records
 * @param checkpoint where each decided segment is committed
 * @param openProjects the opened books
 * @param runs where the run's state is written
 * @param glossary the project's glossary, which preparation scans into when it is empty and each chunk reads
 * @param tm the translation memory each segment is looked up in before it is drafted; the checkpoint writes it
 */
public record RunStores(
        ProjectRepository projects,
        SegmentRepository segments,
        CheckpointPort checkpoint,
        OpenProjects openProjects,
        RunRepository runs,
        GlossaryRepository glossary,
        TmRepository tm) {

    /** Rejects a missing store. */
    public RunStores {
        Objects.requireNonNull(projects, "projects");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(openProjects, "openProjects");
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(tm, "tm");
    }
}
