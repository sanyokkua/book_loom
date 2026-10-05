package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.app.cli.CliRunFakes.ScriptedClock;
import ua.bookloom.app.cli.CliRunFakes.ScriptedModels;
import ua.bookloom.app.cli.CliRunFakes.ScriptedProbe;
import ua.bookloom.pipeline.RecoveryTimer;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The command line run unattended, from the harness run where a provider error ended the command Failed with nothing
 * written (5 accepted and 27 flagged segments discarded): it now waits through an outage like a window run, stops at a
 * pause only a person could end, and writes what it translated either way. Every recovery wait is replayed on a
 * scripted clock.
 */
class TranslateCommandRecoveryTest {

    private static final String BOOK = "One.\n\nTwo.\n\nThree.\n";

    @TempDir
    private Path tempDir;

    private final ScriptedClock clock = new ScriptedClock();

    @Test
    void run_providerOutageMidRun_waitsProbesAndCompletes() throws IOException {
        final ScriptedModels models = new ScriptedModels(Set.of(2, 3), ErrorCode.unreachable);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command(models, new ScriptedProbe(1)).run(arguments(), print(console));

        assertThat(exit).isEqualTo(0);
        assertThat(lines(console))
                .anyMatch(line -> line.startsWith("Provider unreachable — waiting and retrying by itself"))
                .anyMatch(line -> line.startsWith("Completed: "));
        assertThat(Files.readString(tempDir.resolve("Book.uk.md"))).isEqualTo("Один.\n\nДва.\n\nТри.\n");
    }

    @Test
    void run_authErrorMidRun_stopsAndExportsWhatItTranslated() throws IOException {
        final ScriptedModels models = new ScriptedModels(Set.of(2), ErrorCode.auth);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command(models, new ScriptedProbe(0)).run(arguments(), print(console));

        assertThat(exit).isEqualTo(TranslateCommand.PARTIAL_EXIT);
        assertThat(lines(console))
                .anyMatch(line -> line.startsWith("Stopping the run: auth"))
                .anyMatch(line -> line.startsWith("Partial: ") && line.contains("pending=2"));
        assertThat(Files.readString(tempDir.resolve("Book.uk.md"))).isEqualTo("Один.\n\nTwo.\n\nThree.\n");
    }

    @Test
    void run_authErrorWithNoPartial_writesNothing() throws IOException {
        final ScriptedModels models = new ScriptedModels(Set.of(2), ErrorCode.auth);

        final int exit = command(models, new ScriptedProbe(0))
                .run(arguments("--no-partial"), print(new ByteArrayOutputStream()));

        assertThat(exit).isEqualTo(1);
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // The probe never passes: past --max-outage the run would wait for a person, so the command stops it there.
    @Test
    void run_outageLongerThanTheLimit_givesUpAndExportsWhatItTranslated() throws IOException {
        final ScriptedModels models = new ScriptedModels(Set.of(2), ErrorCode.upstream);
        final ByteArrayOutputStream console = new ByteArrayOutputStream();

        final int exit = command(models, new ScriptedProbe(Integer.MAX_VALUE))
                .run(arguments("--max-outage", "5m"), print(console));

        assertThat(exit).isEqualTo(TranslateCommand.PARTIAL_EXIT);
        assertThat(lines(console))
                .anyMatch(line -> line.startsWith("Stopping the run: the provider is still upstream"));
        assertThat(clock.elapsedMinutes()).isBetween(5L, 15L);
    }

    @Test
    void run_reportFlag_writesTheRunsJsonSummary() throws IOException {
        final ScriptedModels models = new ScriptedModels(Set.of(2), ErrorCode.unreachable);
        final Path report = tempDir.resolve("reports/run.json");

        command(models, new ScriptedProbe(0))
                .run(arguments("--report", report.toString()), print(new ByteArrayOutputStream()));

        final JsonNode json = new ObjectMapper().readTree(Files.readString(report));
        assertThat(json.path("provider").asText()).isEqualTo("ollama");
        assertThat(json.path("quality").asText()).isEqualTo("FAST");
        assertThat(json.path("run").path("end").asText()).isEqualTo("COMPLETED");
        assertThat(json.path("run").path("accepted").asInt()).isEqualTo(3);
        assertThat(json.path("recovery").path("outages")).hasSize(1);
        assertThat(json.path("recovery").path("outages").get(0).path("ended").asText())
                .isEqualTo("RESUMED");
        assertThat(json.path("export").path("partial").asBoolean()).isFalse();
        assertThat(json.path("exitCode").asInt()).isEqualTo(0);
        // The scripted models report no usage, so the pipeline's estimate of the reply tokens is what is counted.
        assertThat(json.path("modelCallsByKind").path("DRAFT").path("attempts").asInt())
                .isPositive();
        assertThat(json.path("modelCallsByKind")
                        .path("DRAFT")
                        .path("completionTokens")
                        .asInt())
                .isPositive();
    }

    // A doubled word passes the run's gates as a note; the audit lists the segment, the console says so in one line
    // and the report carries the segment with the check.
    @Test
    void run_acceptedSegmentWithADoubledWord_isListedByTheAuditInTheConsoleAndTheReport() throws IOException {
        final ScriptedModels models = new ScriptedModels(Set.of(), ErrorCode.unreachable, Map.of("Two.", "Два два."));
        final ByteArrayOutputStream console = new ByteArrayOutputStream();
        final Path report = tempDir.resolve("reports/audit.json");

        command(models, new ScriptedProbe(0)).run(arguments("--report", report.toString()), print(console));

        assertThat(lines(console))
                .anyMatch(line -> line.startsWith("Audit: 1 accepted segment(s) look suspicious")
                        && line.contains("duplicate-word"));
        final JsonNode audit =
                new ObjectMapper().readTree(Files.readString(report)).path("audit");
        assertThat(audit.path("suspicious").asInt()).isEqualTo(1);
        assertThat(audit.path("segments").get(0).path("checks").get(0).asText()).isEqualTo("duplicate-word");
        assertThat(audit.path("segments").get(0).path("segmentId").asText()).isEqualTo("Book.md:1");
    }

    @Test
    void run_cleanBook_reportsAnEmptyAuditAndNoAuditLine() throws IOException {
        final ByteArrayOutputStream console = new ByteArrayOutputStream();
        final Path report = tempDir.resolve("reports/clean.json");

        command(new ScriptedModels(Set.of(), ErrorCode.unreachable), new ScriptedProbe(0))
                .run(arguments("--report", report.toString()), print(console));

        assertThat(lines(console)).noneMatch(line -> line.startsWith("Audit:"));
        final JsonNode audit =
                new ObjectMapper().readTree(Files.readString(report)).path("audit");
        assertThat(audit.path("suspicious").asInt()).isZero();
        assertThat(audit.path("segments")).isEmpty();
    }

    @Test
    void run_stopAfterOneSegment_endsTheRunEarlyAndSaysWhy() throws IOException {
        final Path report = tempDir.resolve("reports/stop.json");

        final int exit = command(new ScriptedModels(Set.of(), ErrorCode.unreachable), new ScriptedProbe(0))
                .run(arguments("--stop-after", "1", "--report", report.toString()), print(new ByteArrayOutputStream()));

        final JsonNode json = new ObjectMapper().readTree(Files.readString(report));
        assertThat(json.path("run").path("stoppedBecause").asText()).contains("--stop-after");
        assertThat(json.path("run").path("accepted").asInt()).isLessThan(3);
        assertThat(exit).isEqualTo(TranslateCommand.PARTIAL_EXIT);
    }

    private List<String> arguments(String... extra) throws IOException {
        final Path book = Files.writeString(tempDir.resolve("Book.md"), BOOK, StandardCharsets.UTF_8);
        final List<String> arguments = new ArrayList<>(List.of(
                book.toString(),
                "--from",
                "en",
                "--provider",
                "ollama",
                "--model",
                "gemma4:e4b-mlx",
                "--quality",
                "fast"));
        arguments.addAll(List.of(extra));
        return arguments;
    }

    private TranslateCommand command(ScriptedModels models, ScriptedProbe probe) throws IOException {
        final Path logDir = Files.createDirectories(tempDir.resolve("test-logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(Files.createDirectories(tempDir.resolve("data")), logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
        final Injector core =
                Guice.createInjector(Modules.override(new CoreModules(startup)).with(binder -> {
                    binder.bind(Clock.class).toInstance(clock);
                    binder.bind(RecoveryTimer.class).toInstance(clock::advanceBy);
                }));
        return TranslateCommandTestFakes.commandOver(
                core, models, new TranslateCommandTestFakes.RecordingProviderConfigs(), probe);
    }

    private static PrintStream print(ByteArrayOutputStream console) {
        return new PrintStream(console, true, StandardCharsets.UTF_8);
    }

    private static List<String> lines(ByteArrayOutputStream console) {
        return console.toString(StandardCharsets.UTF_8).lines().toList();
    }
}
