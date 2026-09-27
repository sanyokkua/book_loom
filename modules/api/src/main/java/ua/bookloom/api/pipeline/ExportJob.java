package ua.bookloom.api.pipeline;

import ua.bookloom.api.Result;

/**
 * A single export run, created bound to its {@link ExportRequest} by {@link ExportService#newExport}.
 */
public interface ExportJob {

    /**
     * Runs the export to completion.
     *
     * @return the finished export's report, or a typed failure
     */
    Result<ExportReport> run();

    /**
     * Cancels the export, removing any temporary file, before the atomic move to the destination.
     */
    void cancel();
}
