package ua.bookloom.pipeline.export;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportOpenWork;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.run.RunStores;

/**
 * Refuses a destination the stored project's source cannot be written to, then hands back the job that writes it.
 * The checks run here, before a job exists, so a wrong file type or an alias of the source never reaches a write; the
 * occupied book and side-file paths are the job's own first step, because a file can appear between the two. No
 * export calls a model: writing a book never waits on one.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ExportServiceImpl implements ExportService {

    private final RunStores stores;
    private final DocumentPort documents;
    private final DeferralRepository deferrals;
    private final CallLedger ledger;

    /** An export service whose calls are counted nowhere (tests). */
    public ExportServiceImpl(final RunStores stores, final DocumentPort documents, final DeferralRepository deferrals) {
        this(stores, documents, deferrals, CallLedger.none());
    }

    @Override
    public Result<ExportJob> newExport(final ExportRequest request, @Nullable final ChatModel model) {
        return newExportWith(request, model, ExportMoveOperation.nio());
    }

    @Override
    public Result<ExportJob> newExport(
            final ExportRequest request, @Nullable final ChatModel model, final ExportProgressListener progress) {
        return newExportWith(request, model, ExportMoveOperation.nio(), progress);
    }

    @Override
    public Result<ExportOpenWork> openWork(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("export openWork project={}", projectId);
            return stores.projects().find(projectId).flatMap(found -> openWorkOf(projectId, found));
        } catch (Exception cause) {
            return Result.err(ExportJobImpl.internalError("read the open work", cause));
        }
    }

    /** The publication move is a seam so a test can script its failure. */
    Result<ExportJob> newExportWith(
            final ExportRequest request, @Nullable final ChatModel model, final ExportMoveOperation moves) {
        return newExportWith(request, model, moves, ExportProgressListener.NONE);
    }

    private Result<ExportOpenWork> openWorkOf(final String projectId, final Optional<Project> found) {
        final Document opened = stores.openProjects().get(projectId);
        if (found.isEmpty() || opened == null) {
            log.debug(
                    "export openWork project={} known={} open={}: nothing to read",
                    projectId,
                    found.isPresent(),
                    opened != null);
            return Result.ok(ExportOpenWork.NONE);
        }
        return ProofreadInputs.read(parts(ExportMoveOperation.nio()), found.get(), opened)
                .map(inputs -> OpenWork.of(inputs.book(), inputs.records(), inputs.open()));
    }

    // The model parameter outlives the pass it fed until the Export screen stops passing one.
    private Result<ExportJob> newExportWith(
            final ExportRequest request,
            @Nullable final ChatModel model,
            final ExportMoveOperation moves,
            final ExportProgressListener progress) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progress, "progress");
        try {
            log.debug(
                    "newExport project={} destination={} overwrite={} sideFiles={}",
                    request.projectId(),
                    request.destination(),
                    request.overwrite(),
                    request.sideFiles());
            log.debug("export model ignored withModel={}", model != null);
            final Result<Optional<Project>> found = stores.projects().find(request.projectId());
            if (found.isErr()) {
                return Result.err(Objects.requireNonNull(found.error(), "error"));
            }
            final Optional<Project> project = Objects.requireNonNull(found.data(), "found");
            if (project.isEmpty()) {
                return Result.err(ExportJobImpl.unknownProject(request.projectId()));
            }
            return prepare(request, project.get(), moves, progress);
        } catch (Exception cause) {
            return Result.err(ExportJobImpl.internalError("prepare the export", cause));
        }
    }

    private Result<ExportJob> prepare(
            final ExportRequest request,
            final Project project,
            final ExportMoveOperation moves,
            final ExportProgressListener progress) {
        final Result<Boolean> checked = DestinationChecks.check(project.source(), request.destination());
        if (checked.isErr()) {
            return Result.err(Objects.requireNonNull(checked.error(), "error"));
        }
        return Result.ok(new ExportJobImpl(request, parts(moves), progress, ledger));
    }

    private ExportParts parts(final ExportMoveOperation moves) {
        return new ExportParts(
                stores.projects(),
                stores.segments(),
                stores.openProjects(),
                documents,
                moves,
                deferrals,
                stores.glossary(),
                stores.runs(),
                stores.lexicon());
    }
}
