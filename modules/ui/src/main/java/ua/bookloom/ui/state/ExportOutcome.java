package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.ExportReport;

/**
 * What a finished export leaves for the screen and the completion dialog: the job's report and the size of the file
 * it wrote, which the report does not carry.
 *
 * @param report what the export wrote and verified
 * @param sizeBytes the written file's size in bytes, read once after the export
 */
public record ExportOutcome(ExportReport report, long sizeBytes) {

    /** Rejects an outcome without its report. */
    public ExportOutcome {
        Objects.requireNonNull(report, "report");
    }
}
