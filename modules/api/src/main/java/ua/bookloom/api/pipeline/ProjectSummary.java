package ua.bookloom.api.pipeline;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;

/**
 * One stored project, how much of it is decided and what became of its runs and exports.
 *
 * @param project the stored project
 * @param decidedSegments how many of its stored segments are not pending; {@code 0} when its source file is unreadable
 *     or its content changed since, because stored decisions of another text mean nothing for it
 * @param totalSegments how many segments are stored for it, even when its file changed since
 * @param latestRun the run started last, or {@code null} when the project has never run
 * @param lastExport the export written last, or {@code null} when it was never exported
 */
public record ProjectSummary(
        Project project,
        int decidedSegments,
        int totalSegments,
        @Nullable RunRecord latestRun,
        @Nullable ExportRecord lastExport) {}
