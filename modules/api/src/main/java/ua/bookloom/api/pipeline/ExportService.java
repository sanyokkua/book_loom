package ua.bookloom.api.pipeline;

import ua.bookloom.api.Result;

/**
 * Creates an {@link ExportJob} for a project, the seam the Export screen calls.
 */
public interface ExportService {

    /**
     * Creates a new export job bound to the given request.
     *
     * @param request the non-null export request
     * @return the created job, ready to {@link ExportJob#run()}
     */
    Result<ExportJob> newExport(ExportRequest request);

    /**
     * Creates a job that also announces its progress.
     *
     * @param request the non-null export request
     * @param progress told of each step and finished unit from the thread that runs the job; never null
     * @return the created job; a service that does not report progress ignores {@code progress}
     */
    default Result<ExportJob> newExport(ExportRequest request, ExportProgressListener progress) {
        return newExport(request);
    }

    /**
     * Reads what still waits in a project before its book is written, for the Export screen's banners.
     *
     * @param projectId the non-null project id
     * @return the open work, {@link ExportOpenWork#NONE} for a service that does not read it; or the storage error
     */
    default Result<ExportOpenWork> openWork(String projectId) {
        return Result.ok(ExportOpenWork.NONE);
    }
}
