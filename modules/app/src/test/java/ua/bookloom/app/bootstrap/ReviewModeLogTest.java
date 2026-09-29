package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ua.bookloom.api.pipeline.ReviewMode;

/** The launcher says which review mode it resolved, and a rejected value is named once as a warning. */
class ReviewModeLogTest {

    @TempDir
    private Path dataDir;

    @AfterEach
    void restoreLogging() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
            context.reset();
        }
    }

    private List<String> logAfter(ResolvedReviewMode resolved) throws IOException {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        LoggingBootstrap.configure(
                logDir, false, new ResolvedLogLevel(Level.INFO, ResolvedLogLevel.Source.DEFAULT, null));
        Launcher.logReviewMode(resolved);
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }
        return Files.readAllLines(logDir.resolve("bookloom.log"));
    }

    // WHEN the flag was a typo, THEN exactly one WARN line names it and one INFO line says Unattended was used.
    @Test
    void logReviewMode_rejectedValue_writesOneWarnNamingItAndOneInfoWithTheMode() throws IOException {
        final List<String> lines =
                logAfter(new ResolvedReviewMode(ReviewMode.UNATTENDED, ResolvedReviewMode.Source.DEFAULT, "strict"));

        assertThat(lines)
                .filteredOn(line -> line.contains("WARN"))
                .hasSize(1)
                .allMatch(line -> line.contains("strict"));
        assertThat(lines)
                .filteredOn(line -> line.contains("INFO") && line.contains("review mode"))
                .hasSize(1)
                .allMatch(line -> line.contains("mode=UNATTENDED"));
    }

    // WHEN the mode came from the environment, THEN one INFO line carries the mode and its source and nothing warns.
    @Test
    void logReviewMode_configuredMode_writesOneInfoWithModeAndSourceAndNoWarning() throws IOException {
        final List<String> lines =
                logAfter(new ResolvedReviewMode(ReviewMode.MANUAL, ResolvedReviewMode.Source.ENVIRONMENT, null));

        assertThat(lines).noneMatch(line -> line.contains("WARN"));
        assertThat(lines)
                .filteredOn(line -> line.contains("INFO") && line.contains("review mode"))
                .hasSize(1)
                .allMatch(line -> line.contains("mode=MANUAL source=ENVIRONMENT"));
    }
}
