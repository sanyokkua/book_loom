package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * Where a prompt-eval report came from, so two reports can be compared knowing what changed between them: the code
 * revision, the prompt templates, the model and the settings of the run. Built once per report by {@link #capture}.
 *
 * @param timestamp the ISO-8601 instant the report was made, to the second
 * @param gitSha the short commit of the working tree, {@code unknown} outside a repository
 * @param gitDirty whether the working tree held uncommitted changes
 * @param promptHash the first 12 hex digits of a SHA-256 over the prompt template names and contents
 * @param model the model id
 * @param label the free label of the run, empty when none
 * @param provider {@code ollama} or {@code lmstudio}
 * @param window the context window in tokens the run sized its calls to
 * @param dial the quality dial
 * @param brief the brief preset or file and the overrides, {@code defaults} when none
 * @param suite the suite name
 * @param reasoning the thinking intent of the run's calls, {@code intent-off}: every call asks for thinking off and
 *     the model's profile decides what is sent (design D20), which the run log's {@code profile resolved} line names
 * @param pipelineCommit the full SHA of the commit whose sources the run measured, {@code unknown} outside a repository
 * @param baselineOf the commit whose behaviour this run reproduces when it records a baseline, else null
 * @param dataset the dataset the run read, or null for a suite that has no manifest yet
 * @param quantisation the model's quantisation as the matrix script states it ({@code BOOKLOOM_EVAL_QUANT}), or null
 * @param repeats how many times the dataset was run and with which seeds
 * @param aggregates the run-level score of each repeat; empty until the repeats have run
 * @param judge the judge that compared the run's outputs, or null when none did ({@code mode: none})
 */
@Slf4j
record EvalProvenance(
        String timestamp,
        String gitSha,
        boolean gitDirty,
        String promptHash,
        String model,
        String label,
        String provider,
        int window,
        String dial,
        String brief,
        String suite,
        String reasoning,
        String pipelineCommit,
        @Nullable String baselineOf,
        @Nullable EvalDataset dataset,
        @Nullable String quantisation,
        EvalRepeats repeats,
        List<Double> aggregates,
        JudgeRunner.@Nullable Block judge) {

    static final int REPORT_VERSION = 2;
    private static final double ROUNDING = 1_000_000.0;
    // Every call asks for thinking off; the model's profile decides what reaches the server (design D20), which this
    // report does not know (it depends on what the server said about the model), so the value names the intent only.
    // The run log's "profile resolved" line says what was sent.
    private static final String REASONING = "intent-off";

    private static final String CALIBRATION_RESOURCE = "/eval/judge/calibration.json";
    static final String UNKNOWN = "unknown";
    private static final String PROMPT_DIR = "modules/pipeline/src/main/resources/ua/bookloom/pipeline/prompt";
    private static final int HASH_DIGITS = 12;
    private static final long GIT_TIMEOUT_SECONDS = 10;

    /** Rejects missing text. */
    EvalProvenance {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(gitSha, "gitSha");
        Objects.requireNonNull(promptHash, "promptHash");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(suite, "suite");
        Objects.requireNonNull(reasoning, "reasoning");
        Objects.requireNonNull(pipelineCommit, "pipelineCommit");
        Objects.requireNonNull(repeats, "repeats");
        aggregates = List.copyOf(aggregates);
    }

    /** Provenance of the run in this process: the clock, the checkout and the {@code BOOKLOOM_EVAL_*} settings. */
    static EvalProvenance capture(final String suite, final String model, final String extraLabel) {
        return capture(suite, model, extraLabel, System.getenv(), Clock.systemUTC(), repoRoot(), EvalWindow.window());
    }

    /** As above for a suite that runs at a dial of its own (the judgement suites: Fast unless the env names one). */
    static EvalProvenance capture(
            final String suite, final String model, final String extraLabel, final QualityDial dial) {
        return capture(
                suite,
                model,
                extraLabel,
                System.getenv(),
                Clock.systemUTC(),
                repoRoot(),
                EvalWindow.window(),
                Objects.requireNonNull(dial, "dial").name());
    }

    static EvalProvenance capture(
            final String suite,
            final String model,
            final String extraLabel,
            final Map<String, String> env,
            final Clock clock,
            final Path root,
            final int window) {
        return capture(
                suite,
                model,
                extraLabel,
                env,
                clock,
                root,
                window,
                env.getOrDefault("BOOKLOOM_EVAL_DIAL", "BALANCED").toUpperCase(java.util.Locale.ROOT));
    }

    static EvalProvenance capture(
            final String suite,
            final String model,
            final String extraLabel,
            final Map<String, String> env,
            final Clock clock,
            final Path root,
            final int window,
            final String dial) {
        final String label = EvalBriefLabel.joinLabel(env.getOrDefault("BOOKLOOM_EVAL_LABEL", ""), extraLabel);
        final List<String> git = git(root);
        return new EvalProvenance(
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(),
                git.get(0),
                Boolean.parseBoolean(git.get(1)),
                promptHash(root.resolve(PROMPT_DIR)),
                model,
                label,
                "lmstudio".equalsIgnoreCase(env.get("BOOKLOOM_EVAL_PROVIDER")) ? "lmstudio" : "ollama",
                window,
                dial,
                EvalBriefLabel.briefSummary(env),
                suite,
                REASONING,
                commit(root),
                blankToNull(env.get("BOOKLOOM_EVAL_BASELINE_OF")),
                datasetOf(suite),
                blankToNull(env.get("BOOKLOOM_EVAL_QUANT")),
                EvalRepeats.fromEnvironment(env),
                List.of(),
                null);
    }

    /** The manifest-checked dataset a suite reads, or null for a suite that has no manifest yet. */
    private static @Nullable EvalDataset datasetOf(final String suite) {
        return switch (suite) {
            case MiniReport.SUITE -> EvalDataset.of(DatasetManifest.loadBundled(MiniBook.MINI_EN));
            case JudgeCalibration.SUITE -> EvalDataset.of(DatasetManifest.loadBundled(CalItems.CAL_1));
            case "sequence" -> EvalDataset.of(DatasetManifest.loadBundled("sequence"));
            default -> null;
        };
    }

    /** The same provenance reading the bundled dataset folder {@code id}, for a suite with more than one dataset. */
    EvalProvenance withDataset(final String id) {
        return copy(EvalDataset.of(DatasetManifest.loadBundled(id)), aggregates, judge);
    }

    /** The same provenance once the repeats have run and left their run-level scores. */
    EvalProvenance withAggregates(final List<Double> scores) {
        return copy(dataset, scores, judge);
    }

    /** The same provenance with the judge that compared the run's outputs. */
    EvalProvenance withJudge(final JudgeRunner.Block block) {
        return copy(dataset, aggregates, Objects.requireNonNull(block, "block"));
    }

    private EvalProvenance copy(
            final @Nullable EvalDataset read, final List<Double> scores, final JudgeRunner.@Nullable Block block) {
        return new EvalProvenance(
                timestamp,
                gitSha,
                gitDirty,
                promptHash,
                model,
                label,
                provider,
                window,
                dial,
                brief,
                suite,
                reasoning,
                pipelineCommit,
                baselineOf,
                read,
                quantisation,
                repeats,
                scores,
                block);
    }

    /** The spread of the repeats' run-level scores (largest minus smallest), or null with fewer than two. */
    @Nullable
    Double noiseBand() {
        if (aggregates.size() < 2) {
            return null;
        }
        final double band = Collections.max(aggregates) - Collections.min(aggregates);
        final double rounded = Math.round(band * ROUNDING) / ROUNDING;
        log.debug("Eval noise band {} over {} aggregates", rounded, aggregates.size());
        return rounded;
    }

    /** The directory name of this run in the history: {@code yyyyMMdd-HHmmss-suite-model[-label]}. */
    String runName() {
        final String stamp = timestamp.replace("-", "").replace(":", "").replace('T', '-');
        return safe(stamp.replaceAll("Z$", "") + "-" + suite + "-" + model + (label.isEmpty() ? "" : "-" + label));
    }

    /** The provenance as one JSON object. */
    String json() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", timestamp);
        fields.put("gitSha", gitSha);
        fields.put("gitDirty", gitDirty);
        fields.put("promptHash", promptHash);
        fields.put("version", REPORT_VERSION);
        fields.put("pipelineCommit", pipelineCommit);
        fields.put("baselineOf", baselineOf);
        fields.put("model", modelBlock());
        fields.put("label", label);
        fields.put("provider", provider);
        fields.put("window", window);
        fields.put("dial", dial);
        fields.put("brief", brief);
        fields.put("suite", suite);
        fields.put("reasoning", reasoning);
        fields.put("dataset", dataset);
        fields.put("judge", judge == null ? Map.of("mode", "none") : judgeJson(judge));
        fields.put("repeats", Map.of("k", repeats.k(), "seeds", repeats.seeds()));
        fields.put("aggregates", aggregates);
        fields.put("noiseBand", noiseBand());
        fields.put("comparison", null);
        try {
            return new ObjectMapper().writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The judge block with the bundled calibration result, the evidence of how far the judge may be trusted. */
    private static Map<String, Object> judgeJson(final JudgeRunner.Block block) {
        final Map<String, Object> fields = block.json();
        fields.put("calibration", calibrationResult());
        return fields;
    }

    private static Object calibrationResult() {
        try (InputStream in = Objects.requireNonNull(
                EvalProvenance.class.getResourceAsStream(CALIBRATION_RESOURCE), CALIBRATION_RESOURCE)) {
            return new ObjectMapper().readValue(in, Object.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> modelBlock() {
        final Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", model);
        block.put("provider", provider);
        block.put("server", null);
        block.put("serverVersion", null);
        block.put("quantisation", quantisation);
        block.put("contextWindow", window);
        block.put("profileId", null);
        block.put("thinking", reasoning);
        block.put("sampling", null);
        block.put("seedPolicy", "base+repeat");
        block.put("seeds", repeats.seeds());
        return block;
    }

    /** A report's JSON object with the {@code provenance} key added first. */
    String within(final String reportJson) {
        final String body = reportJson.strip();
        if (!body.startsWith("{")) {
            throw new IllegalArgumentException("not a JSON object");
        }
        final String rest = body.substring(1).stripLeading();
        return "{\"provenance\":" + json() + (rest.startsWith("}") ? "" : ",") + rest;
    }

    /** SHA-256 over the sorted relative names and contents of the files under a directory, first 12 hex digits. */
    static String promptHash(final Path dir) {
        if (!Files.isDirectory(dir)) {
            return UNKNOWN;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (final Path file : files) {
                digest.update(dir.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(file));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, HASH_DIGITS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The short SHA and the dirty flag ({@code true}/{@code false}) of a checkout; {@code unknown, false} otherwise. */
    static List<String> git(final Path dir) {
        final String sha = run(dir, "git", "rev-parse", "--short", "HEAD");
        if (sha == null || sha.isBlank()) {
            return List.of(UNKNOWN, "false");
        }
        final String status = run(dir, "git", "status", "--porcelain", "--untracked-files=no");
        return List.of(sha.strip(), String.valueOf(status != null && !status.isBlank()));
    }

    /** The full SHA of the checkout's HEAD; {@code unknown} outside a repository. */
    static String commit(final Path dir) {
        final String sha = run(dir, "git", "rev-parse", "HEAD");
        return sha == null || sha.isBlank() ? UNKNOWN : sha.strip();
    }

    private static @Nullable String blankToNull(@Nullable final String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** The checkout root: the nearest ancestor of the working directory that holds {@code AGENTS.md}. */
    static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("AGENTS.md"))) {
            dir = dir.getParent();
        }
        return dir == null ? Path.of("").toAbsolutePath() : dir;
    }

    private static String run(final Path dir, final String... command) {
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start();
            final String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            return out;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String safe(final String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
