package ua.bookloom.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.ConsistencySummary;
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
    private final List<@Nullable ChatModel> models = new CopyOnWriteArrayList<>();
    private final List<Boolean> ranOnFxThread = new CopyOnWriteArrayList<>();
    private volatile @Nullable AppError failure;
    private volatile @Nullable ExportReport report;
    private volatile boolean isWritingFiles;

    /** Makes every successful export answer with {@code scripted}; {@code null} answers a one-segment report. */
    public void reportWith(@Nullable final ExportReport scripted) {
        report = scripted;
    }

    /** Makes every successful export create its destination file, as a real one does, holding one byte. */
    public void writeFiles(final boolean writing) {
        isWritingFiles = writing;
    }

    /** Makes every export from now on fail with {@code error}; {@code null} makes them succeed again. */
    public void failWith(@Nullable final AppError error) {
        failure = error;
    }

    @Override
    public Result<ExportJob> newExport(final ExportRequest request, @Nullable final ChatModel model) {
        requests.add(Objects.requireNonNull(request, "request"));
        models.add(model);
        return Result.ok(new ScriptedExportJob(request));
    }

    /** Every request asked for, in order. */
    public List<ExportRequest> requests() {
        return List.copyOf(requests);
    }

    /** The model each request was made with, in order; an entry is {@code null} when none was passed. */
    public List<@Nullable ChatModel> models() {
        return Collections.unmodifiableList(new ArrayList<>(models));
    }

    /** One entry per {@code run()}: {@code true} when it ran on the FX Application Thread. */
    public List<Boolean> ranOnFxThread() {
        return List.copyOf(ranOnFxThread);
    }

    private final class ScriptedExportJob implements ExportJob {

        private final ExportRequest request;

        ScriptedExportJob(final ExportRequest request) {
            this.request = request;
        }

        @Override
        public Result<ExportReport> run() {
            ranOnFxThread.add(Platform.isFxApplicationThread());
            final AppError error = failure;
            if (error != null) {
                return Result.err(error);
            }
            writeDestination();
            final ExportReport scripted = report;
            if (scripted != null) {
                return Result.ok(scripted);
            }
            final Path destination = request.destination();
            return Result.ok(
                    new ExportReport(destination, 1, 0, 0, 0, 0, 0, List.of(), 0, ConsistencySummary.NOT_RUN, 0));
        }

        private void writeDestination() {
            if (!isWritingFiles) {
                return;
            }
            try {
                Files.write(request.destination(), new byte[1]);
            } catch (IOException cause) {
                throw new UncheckedIOException(cause);
            }
        }

        @Override
        public void cancel() {
            // A scripted export finishes at once, so there is nothing to interrupt.
        }
    }
}
