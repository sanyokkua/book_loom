package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.app.cli.TranslateCommandTestFakes.RecordingChatModelFactory;
import static ua.bookloom.app.cli.TranslateCommandTestFakes.RecordingProviderConfigs;
import static ua.bookloom.app.cli.TranslateCommandTestFakes.ScriptedProviderVerifier;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** A kill during the export, delivered through the real {@link ShutdownCancellation}, leaves no partial output. */
class TranslateCommandShutdownTest {

    @TempDir
    private Path tempDir;

    // WHEN the shutdown hook fires while the export is held, THEN the real export removes its hidden temporary file.
    @Test
    void run_shutdownDuringExport_leavesNeitherOutputNorHiddenTemporaryFile() throws IOException, InterruptedException {
        final Path source = Files.writeString(tempDir.resolve("Book.md"), "He opened the *old* door.\n");
        final Injector core = core();
        final ShutdownCancellation shutdown = core.getInstance(ShutdownCancellation.class);
        final KilledExportService exports = new KilledExportService(core.getInstance(ExportService.class), shutdown);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = TranslateCommandTestFakes.commandOver(
                        core,
                        new RecordingChatModelFactory(),
                        new RecordingProviderConfigs(),
                        new ScriptedProviderVerifier(new VerificationReport(List.of())),
                        exports)
                .run(List.of(source.toString()), new PrintStream(console, true, StandardCharsets.UTF_8));
        exports.awaitShutdownReturned();

        assertThat(exit).isEqualTo(1);
        assertThat(console.toString(StandardCharsets.UTF_8))
                .contains("Export cancelled: The translated book was checked but not published");
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
        assertThat(names()).containsExactlyInAnyOrder("Book.md", "data", "test-logs");
    }

    private Injector core() throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("test-logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(Files.createDirectories(tempDir.resolve("data")), logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
        return Guice.createInjector(new CoreModules(startup));
    }

    private List<String> names() throws IOException {
        try (Stream<Path> paths = Files.list(tempDir)) {
            return paths.map(path -> path.getFileName().toString()).toList();
        }
    }

    /** Delivers the shutdown hook's run on another thread as soon as the export starts, as a Ctrl+C would. */
    private static final class KilledExportService implements ExportService {
        private final ExportService real;
        private final ShutdownCancellation shutdown;
        private final CountDownLatch shutdownReturned = new CountDownLatch(1);

        KilledExportService(ExportService real, ShutdownCancellation shutdown) {
            this.real = real;
            this.shutdown = shutdown;
        }

        @Override
        public Result<ExportJob> newExport(ExportRequest request, @Nullable ChatModel model) {
            final Result<ExportJob> created = real.newExport(request, model);
            final ExportJob job = created.data();
            return job == null ? created : Result.ok(new KilledExportJob(job));
        }

        void awaitShutdownReturned() throws InterruptedException {
            assertThat(shutdownReturned.await(10, TimeUnit.SECONDS)).isTrue();
        }

        private final class KilledExportJob implements ExportJob {
            private final ExportJob delegate;
            private final CountDownLatch cancelled = new CountDownLatch(1);

            KilledExportJob(ExportJob delegate) {
                this.delegate = delegate;
            }

            @Override
            public Result<ExportReport> run() {
                Thread.ofVirtual().start(() -> {
                    shutdown.run();
                    shutdownReturned.countDown();
                });
                try {
                    assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return delegate.run();
            }

            @Override
            public void cancel() {
                delegate.cancel();
                cancelled.countDown();
            }
        }
    }
}
