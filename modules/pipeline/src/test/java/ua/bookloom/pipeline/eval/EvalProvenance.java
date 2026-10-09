package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
 */
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
        String suite) {

    static final String UNKNOWN = "unknown";
    private static final String PROMPT_DIR = "modules/pipeline/src/main/resources/ua/bookloom/pipeline/prompt";
    private static final int HASH_DIGITS = 12;
    private static final long GIT_TIMEOUT_SECONDS = 10;
    private static final String[] BRIEF_KEYS = {"PRESET", "GOLD", "BRIEF", "REGISTER", "NAMES", "GENRE"};

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
    }

    /** Provenance of the run in this process: the clock, the checkout and the {@code BOOKLOOM_EVAL_*} settings. */
    static EvalProvenance capture(final String suite, final String model, final String extraLabel) {
        return capture(suite, model, extraLabel, System.getenv(), Clock.systemUTC(), repoRoot(), EvalWindow.window());
    }

    static EvalProvenance capture(
            final String suite,
            final String model,
            final String extraLabel,
            final Map<String, String> env,
            final Clock clock,
            final Path root,
            final int window) {
        final String label = joinLabel(env.getOrDefault("BOOKLOOM_EVAL_LABEL", ""), extraLabel);
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
                env.getOrDefault("BOOKLOOM_EVAL_DIAL", "BALANCED").toUpperCase(java.util.Locale.ROOT),
                briefSummary(env),
                suite);
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
        fields.put("model", model);
        fields.put("label", label);
        fields.put("provider", provider);
        fields.put("window", window);
        fields.put("dial", dial);
        fields.put("brief", brief);
        fields.put("suite", suite);
        try {
            return new ObjectMapper().writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
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

    private static String briefSummary(final Map<String, String> env) {
        final StringBuilder out = new StringBuilder();
        for (final String key : BRIEF_KEYS) {
            final String value = env.get("BOOKLOOM_EVAL_" + key);
            if (value != null && !value.isBlank()) {
                final String shown = "BRIEF".equals(key) || "GOLD".equals(key)
                        ? Path.of(value).getFileName().toString()
                        : value;
                out.append(out.length() == 0 ? "" : " ")
                        .append(key.toLowerCase(java.util.Locale.ROOT))
                        .append('=')
                        .append(shown);
            }
        }
        return out.length() == 0 ? "defaults" : out.toString();
    }

    private static String joinLabel(final String first, final String second) {
        return Stream.of(first, second)
                .filter(part -> !part.isBlank())
                .collect(java.util.stream.Collectors.joining("-"));
    }

    private static String safe(final String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
