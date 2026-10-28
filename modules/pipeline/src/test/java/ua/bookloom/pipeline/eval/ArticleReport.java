package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Objects;

/**
 * One article run as the table printed to the log and the JSON the matrix reads; the JSON keeps every segment's target,
 * so a baseline holds the invented text the run produced.
 *
 * @param model the model's name
 * @param dial the quality dial of the run
 * @param run what the run left
 */
record ArticleReport(String model, String dial, ArticleRun run) {

    static final String SUITE = "article";

    /** Rejects a missing part. */
    ArticleReport {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(run, "run");
    }

    /** The history label of the run: {@code en-<target>-<dial>}. */
    String label() {
        return ArticleGravity.SOURCE + "-" + run.target() + "-" + dial.toLowerCase(Locale.ROOT);
    }

    String json() {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode root = mapper.createObjectNode();
        root.put("suite", SUITE);
        root.put("model", model);
        root.put("dial", dial);
        root.put("source", ArticleGravity.SOURCE);
        root.put("target", run.target());
        root.put("end", run.end().name());
        root.put("segments", run.segments());
        root.put("flagged", run.flagged());
        root.put("flaggedRate", run.flaggedRate());
        root.put("protectedSpansKept", run.keptShare());
        root.putPOJO("protectedSpansLost", run.lostSpans());
        root.put("calls", run.calls());
        root.put("elapsedSeconds", run.elapsed().toMillis() / 1000.0);
        final ObjectNode outputs = root.putObject("outputs");
        run.outputs().forEach(outputs::put);
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    String table() {
        return String.format(
                Locale.ROOT,
                "promptEval article model=%s dial=%s pair=%s-%s end=%s%n"
                        + "segments %d  flagged %d (%.1f%%)  protectedSpansKept %.1f%%  lost %s%n"
                        + "calls %d  elapsedSec %.1f",
                model,
                dial,
                ArticleGravity.SOURCE,
                run.target(),
                run.end(),
                run.segments(),
                run.flagged(),
                100 * run.flaggedRate(),
                100 * run.keptShare(),
                run.lostSpans(),
                run.calls(),
                run.elapsed().toMillis() / 1000.0);
    }
}
