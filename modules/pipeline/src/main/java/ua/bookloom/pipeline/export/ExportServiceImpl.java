package ua.bookloom.pipeline.export;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * Refuses a destination the stored project's source cannot be written to, then hands back the job that writes it.
 * The checks run here, before a job exists, so a wrong file type or an alias of the source never reaches a write.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ExportServiceImpl implements ExportService {

    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final OpenProjects openProjects;
    private final DocumentPort documents;

    @Override
    public Result<ExportJob> newExport(final ExportRequest request, @Nullable final ChatModel model) {
        return newExportWith(request, ExportMoveOperation.nio());
    }

    /** The publication move is a seam so a test can script its failure. */
    Result<ExportJob> newExportWith(final ExportRequest request, final ExportMoveOperation moves) {
        Objects.requireNonNull(request, "request");
        try {
            log.debug(
                    "newExport project={} destination={} overwrite={}",
                    request.projectId(),
                    request.destination(),
                    request.overwrite());
            final Result<Optional<Project>> found = projects.find(request.projectId());
            if (found.isErr()) {
                return Result.err(Objects.requireNonNull(found.error(), "error"));
            }
            final Optional<Project> project = Objects.requireNonNull(found.data(), "found");
            if (project.isEmpty()) {
                return Result.err(ExportJobImpl.unknownProject(request.projectId()));
            }
            return prepare(request, project.get(), moves);
        } catch (Throwable cause) {
            return Result.err(ExportJobImpl.internalError("prepare the export", cause));
        }
    }

    private Result<ExportJob> prepare(
            final ExportRequest request, final Project project, final ExportMoveOperation moves) {
        final Result<Boolean> checked = DestinationChecks.check(project.source(), request.destination());
        if (checked.isErr()) {
            return Result.err(Objects.requireNonNull(checked.error(), "error"));
        }
        return Result.ok(new ExportJobImpl(request, projects, segments, openProjects, documents, moves));
    }
}
