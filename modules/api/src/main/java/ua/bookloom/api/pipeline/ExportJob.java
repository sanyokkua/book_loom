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
     * Cancels the export; {@link #run()} then answers {@code cancelled}. A cancel seen before the book's atomic move
     * stops any further model call and leaves neither the book nor a temporary file behind; one seen after the move
     * stops the side files not yet written. It may be called before or during {@link #run()}, from any thread.
     */
    void cancel();
}
