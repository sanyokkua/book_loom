package ua.bookloom.api.pipeline;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;

/**
 * Creates an {@link ExportJob} for a project, the seam the Export screen calls.
 */
public interface ExportService {

    /**
     * Creates a new export job bound to the given request.
     *
     * @param request the non-null export request
     * @param model the model the consistency pass calls, or null when {@code request.consistencyPass()} is false
     * @return the created job, ready to {@link ExportJob#run()}
     */
    Result<ExportJob> newExport(ExportRequest request, @Nullable ChatModel model);
}
