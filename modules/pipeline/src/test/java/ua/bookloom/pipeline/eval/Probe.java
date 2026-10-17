package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.eval.ProbeCase.Sending;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * The fast prompt-eval loop: one case, one direct call to the loaded model through the production prompt builders, read
 * the request, the reply and the verdict, adjust, repeat. Run it with {@code scripts/probe.sh}; the requests are built
 * by the same classes the eval suites use ({@link PromptEvalRunner}, {@link RealRunRunner} over an {@link EvalProject}),
 * and every call goes through {@link JobModelCalls}, so what is printed is what the suite would send.
 *
 * <pre>
 * --case &lt;id&gt;            a prompt case, a defects.json id or a realrun case id
 * --list [suite]          the case ids (prompt, defects or realrun)
 * --repeat N              send the case N times (default 1)
 * --dry                   build and print the request, make no call
 * --provider ollama|lmstudio|pseudo   (default ollama; pseudo answers locally, for tests)
 * --url U  --model M      endpoint (the provider's usual local one) and model (default gemma4:e4b-mlx)
 * --window N              context window (default 16384, constant so Ollama never reloads the model)
 * </pre>
 *
 * Realrun cases build their requests against {@link EvalProject#window()}, the window every suite shares.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Probe {

    private static final int USAGE_ERROR = 2;

    /** Runs the probe and exits with its status. */
    public static void main(final String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Runs the probe; returns 0, or 2 for a usage error or an unknown case. */
    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        final ProbeArgs parsed;
        try {
            parsed = ProbeArgs.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("probe: " + e.getMessage());
            err.println("usage: --case <id> | --list [suite]  [--repeat N] [--dry] [--provider ollama|lmstudio|pseudo]"
                    + " [--url U] [--model M] [--window N]");
            return USAGE_ERROR;
        }
        log.debug(
                "Probe args case={} list={} repeat={} dry={}",
                parsed.caseId(),
                parsed.list(),
                parsed.repeat(),
                parsed.dry());
        if (parsed.list()) {
            return list(parsed.listSuite(), out, err);
        }
        final String id = java.util.Objects.requireNonNull(parsed.caseId());
        final Optional<ProbeCase> found = ProbeCases.find(id);
        if (found.isEmpty()) {
            err.println("probe: unknown case '" + id + "'");
            err.println("closest: " + String.join(", ", ProbeCases.closest(id)));
            return USAGE_ERROR;
        }
        return send(found.get(), parsed, out);
    }

    private static int list(final String suite, final PrintStream out, final PrintStream err) {
        final List<String> suites = List.of(ProbeCases.PROMPT, ProbeCases.DEFECTS, ProbeCases.REALRUN);
        if (suite != null && !suites.contains(suite)) {
            err.println("probe: unknown suite '" + suite + "', one of " + suites);
            return USAGE_ERROR;
        }
        for (final String name : suites) {
            if (suite == null || suite.equals(name)) {
                out.println("[" + name + "]");
                ProbeCases.all().stream().filter(c -> c.suite().equals(name)).forEach(c -> out.println("  " + c.id()));
            }
        }
        return 0;
    }

    private static int send(final ProbeCase probeCase, final ProbeArgs args, final PrintStream out) {
        final boolean dry = args.dry();
        final ProbeModel model = new ProbeModel(dry ? null : chatModel(args));
        final JobModelCalls calls = new JobModelCalls(
                onSent -> model, event -> {}, Clock.systemUTC(), PromptEvalCases.TARGET_LANGUAGE, args.window());
        final Path workDir = temporaryDirectory();
        try {
            out.println("probe case=" + probeCase.id() + " suite=" + probeCase.suite() + " provider=" + args.provider()
                    + " model=" + args.model() + " window=" + args.window() + " repeat=" + args.repeat()
                    + (dry ? " dry" : ""));
            final ProbeReport report = new ProbeReport(out);
            for (int i = 1; i <= args.repeat(); i++) {
                model.reset();
                final List<String> verdict = probeCase.run().apply(new Sending(calls, args.window(), workDir));
                report.repeat(i, args.repeat(), model.exchanges(), verdict, dry);
            }
            return 0;
        } finally {
            delete(workDir);
        }
    }

    private static ChatModel chatModel(final ProbeArgs args) {
        if (args.isPseudo()) {
            return new PseudoChatModel(new ObjectMapper());
        }
        return PromptEvalTest.chatModel(
                PromptEvalTest.registeredProvider(args.endpoint(), args.isLmStudio()), args.model());
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("probe");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void delete(final Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            for (final Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.debug("Probe could not remove {}: {}", directory, e.toString());
        }
    }
}
