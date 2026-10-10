package ua.bookloom.pipeline.export;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.revision.ConsistencyPass;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * Refuses a destination the stored project's source cannot be written to, then hands back the job that writes it.
 * The checks run here, before a job exists, so a wrong file type or an alias of the source never reaches a write; the
 * occupied book and side-file paths are the job's own first step, because a file can appear between the two.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ExportServiceImpl implements ExportService {

    private static final String UNKNOWN_LANGUAGE = "und";

    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final OpenProjects openProjects;
    private final DocumentPort documents;
    private final ConsistencyPass consistencyPass;
    private final GlossaryRepository glossary;
    private final RunRepository runs;
    private final Clock clock;

    @Override
    public Result<ExportJob> newExport(final ExportRequest request, @Nullable final ChatModel model) {
        return newExportWith(request, model, ExportMoveOperation.nio());
    }

    @Override
    public Result<ExportJob> newExport(
            final ExportRequest request, @Nullable final ChatModel model, final ExportProgressListener progress) {
        return newExportWith(request, model, ExportMoveOperation.nio(), progress);
    }

    /** The publication move is a seam so a test can script its failure. */
    Result<ExportJob> newExportWith(
            final ExportRequest request, @Nullable final ChatModel model, final ExportMoveOperation moves) {
        return newExportWith(request, model, moves, ExportProgressListener.NONE);
    }

    private Result<ExportJob> newExportWith(
            final ExportRequest request,
            @Nullable final ChatModel model,
            final ExportMoveOperation moves,
            final ExportProgressListener progress) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progress, "progress");
        try {
            log.debug(
                    "newExport project={} destination={} overwrite={} sideFiles={} consistencyPass={} withModel={}",
                    request.projectId(),
                    request.destination(),
                    request.overwrite(),
                    request.sideFiles(),
                    request.consistencyPass(),
                    model != null);
            final Result<Optional<Project>> found = projects.find(request.projectId());
            if (found.isErr()) {
                return Result.err(Objects.requireNonNull(found.error(), "error"));
            }
            final Optional<Project> project = Objects.requireNonNull(found.data(), "found");
            if (project.isEmpty()) {
                return Result.err(ExportJobImpl.unknownProject(request.projectId()));
            }
            return prepare(request, project.get(), model, moves, progress);
        } catch (Throwable cause) {
            return Result.err(ExportJobImpl.internalError("prepare the export", cause));
        }
    }

    private Result<ExportJob> prepare(
            final ExportRequest request,
            final Project project,
            @Nullable final ChatModel model,
            final ExportMoveOperation moves,
            final ExportProgressListener progress) {
        final Result<Boolean> checked = DestinationChecks.check(project.source(), request.destination());
        if (checked.isErr()) {
            return Result.err(Objects.requireNonNull(checked.error(), "error"));
        }
        // The pass's calls reach the model the person chose; without one, only the name sweep runs.
        final ModelCalls calls = model == null ? null : shownCalls(project, model, progress);
        final ExportParts parts =
                new ExportParts(projects, segments, openProjects, documents, moves, consistencyPass, glossary, runs);
        return Result.ok(new ExportJobImpl(request, parts, calls, progress));
    }

    // The pass's calls are timed and shown as a run's are: each described call reaches the listener as a snapshot.
    private ModelCalls shownCalls(final Project project, final ChatModel model, final ExportProgressListener progress) {
        final Document opened = openProjects.get(project.id());
        final String target = Objects.requireNonNullElse(project.brief().targetLanguage(), UNKNOWN_LANGUAGE);
        log.debug(
                "export calls are shown project={} bookOpen={} targetLanguage={}",
                project.id(),
                opened != null,
                target);
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                event -> {
                    if (event instanceof CallSnapshotUpdated updated) {
                        progress.onCall(updated.snapshot());
                    }
                },
                clock,
                target,
                ContextBudget.DEFAULT_WINDOW,
                opened == null ? Map.of() : SegmentLocators.of(opened));
    }
}
