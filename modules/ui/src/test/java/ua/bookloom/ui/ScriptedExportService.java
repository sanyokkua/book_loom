package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;

/**
 * A hand-written {@link ExportService} that records each request and answers with one scripted report, or one scripted
 * error when the export is scripted to fail.
 */
public final class ScriptedExportService implements ExportService {

    private final List<ExportRequest> requests = new CopyOnWriteArrayList<>();
    private volatile @Nullable AppError failure;

    /** Makes every export from now on fail with {@code error}; {@code null} makes them succeed again. */
    public void failWith(@Nullable final AppError error) {
        failure = error;
    }

    @Override
    public Result<ExportJob> newExport(final ExportRequest request, @Nullable final ChatModel model) {
        requests.add(Objects.requireNonNull(request, "request"));
        return Result.ok(new ScriptedExportJob(request));
    }

    /** Every request asked for, in order. */
    public List<ExportRequest> requests() {
        return List.copyOf(requests);
    }

    private final class ScriptedExportJob implements ExportJob {

        private final ExportRequest request;

        ScriptedExportJob(final ExportRequest request) {
            this.request = request;
        }

        @Override
        public Result<ExportReport> run() {
            final AppError error = failure;
            if (error != null) {
                return Result.err(error);
            }
            final Path destination = request.destination();
            return Result.ok(new ExportReport(destination, 1, 0, 0, 0, 0, 0, List.of(), 0));
        }

        @Override
        public void cancel() {
            // A scripted export finishes at once, so there is nothing to interrupt.
        }
    }
}
