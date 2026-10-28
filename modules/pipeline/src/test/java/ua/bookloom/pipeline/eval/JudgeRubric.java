package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.util.hash.HashUtil;

/**
 * The judge's rubric {@code bl-judge-1}: one prompt file with the rules every judgement shares and the reply shape of
 * each mode. Its hash is part of every cached reply's key and of the report, so editing one character of the rubric
 * can never be compared as if it were the same judge.
 *
 * @param common the rules both modes share
 * @param pointwise the instructions and reply shape of a single translation's judgement
 * @param pairwise the instructions and reply shape of a two-translation comparison
 * @param hash the first 12 hex digits of the SHA-256 of the file's bytes, as {@code shasum -a 256} prints it
 */
@Slf4j
record JudgeRubric(String common, String pointwise, String pairwise, String hash) {

    static final String VERSION = "bl-judge-1";
    private static final String RESOURCE = "/eval/judge/" + VERSION + ".prompt";
    private static final int HASH_DIGITS = 12;
    private static final Pattern SECTIONS = Pattern.compile("(?s)@@common\\n(.*?)@@pointwise\\n(.*?)@@pairwise\\n(.*)");

    /** What the judge is asked to do; each mode carries its name in the cache key and the report. */
    enum Mode {
        /** One translation scored on four dimensions with its errors. */
        POINTWISE("pointwise"),
        /** Two translations of one source, the better one named. */
        PAIRWISE("pairwise");

        private final String wire;

        Mode(final String wire) {
            this.wire = wire;
        }

        String wire() {
            return wire;
        }
    }

    /** Rejects a missing section. */
    JudgeRubric {
        Objects.requireNonNull(common, "common");
        Objects.requireNonNull(pointwise, "pointwise");
        Objects.requireNonNull(pairwise, "pairwise");
        Objects.requireNonNull(hash, "hash");
    }

    /** The bundled rubric. */
    static JudgeRubric load() {
        try (InputStream in = JudgeRubric.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            final byte[] bytes = in.readAllBytes();
            final JudgeRubric rubric = of(new String(bytes, StandardCharsets.UTF_8), hashOf(bytes));
            log.debug("Judge rubric loaded version={} hash={} bytes={}", VERSION, rubric.hash(), bytes.length);
            return rubric;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A rubric file being tuned (design D24: at most three rounds on the dev items) before it is frozen into the
     * bundled file; its own hash keys the cache and the report, so a tuned rubric never reads as the frozen one.
     */
    static JudgeRubric load(final Path file) {
        Objects.requireNonNull(file, "file");
        try {
            final byte[] bytes = Files.readAllBytes(file);
            final JudgeRubric rubric = of(new String(bytes, StandardCharsets.UTF_8), hashOf(bytes));
            log.info("Judge rubric loaded from a file file={} hash={} bytes={}", file, rubric.hash(), bytes.length);
            return rubric;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JudgeRubric of(final String text, final String hash) {
        final Matcher sections = SECTIONS.matcher(text);
        if (!sections.matches()) {
            throw new IllegalStateException(RESOURCE + " lacks its @@common, @@pointwise and @@pairwise sections");
        }
        return new JudgeRubric(
                sections.group(1).strip(),
                sections.group(2).strip(),
                sections.group(3).strip(),
                hash);
    }

    private static String hashOf(final byte[] bytes) {
        return HashUtil.sha256Hex(bytes).substring(0, HASH_DIGITS);
    }

    /** The system prompt of {@code mode}: the shared rules, then the mode's own instructions and reply shape. */
    String system(final Mode mode) {
        Objects.requireNonNull(mode, "mode");
        return common
                + "\n\n"
                + switch (mode) {
                    case POINTWISE -> pointwise;
                    case PAIRWISE -> pairwise;
                };
    }
}
