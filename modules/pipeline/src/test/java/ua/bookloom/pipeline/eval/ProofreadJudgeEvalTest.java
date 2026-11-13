package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.pipeline.eval.JudgeRunner.PairVerdict;

/**
 * The judge screen of a Proofread arm (design D24, arm 7c; arm 7b grades the same way): two mini runs of one dataset,
 * paired repeat by repeat (the same seeds), and the local judge asked pairwise, with the order swapped, about each
 * changed paragraph — a block whose Proofread rewrite either side kept. Per side it also compares each kept rewrite
 * with the text it replaced, so a prompt that edits more is not mistaken for one that edits better. The judge is a
 * screen only (arm 1): every discordant case is read by hand. Local-only: {@code BOOKLOOM_EVAL_SUITE=proofread-judge
 * BOOKLOOM_EVAL_URL=… ./gradlew :pipeline:promptEval --tests '*ProofreadJudgeEvalTest'}, with
 * {@code BOOKLOOM_EVAL_JUDGE_A} and {@code BOOKLOOM_EVAL_JUDGE_B} the two runs' {@code report.json} files and
 * {@code BOOKLOOM_EVAL_JUDGE_OUT} the JSON file written (absolute paths; a {@code .txt} table lands beside it).
 * {@code BOOKLOOM_EVAL_MODEL} is the judge (default {@code gemma4:26b-mlx}), thinking off.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "proofread-judge")
class ProofreadJudgeEvalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEPT = "KEPT";

    private record Side(JsonNode report, String label) {

        JsonNode repeat(final int index) {
            return report.get("repeats").get(index);
        }

        int repeats() {
            return report.get("repeats").size();
        }

        boolean isKept(final int repeat, final String id) {
            for (final JsonNode read : repeat(repeat).get("proofread")) {
                if (read.get("id").asText().equals(id)
                        && KEPT.equals(read.get("outcome").asText())) {
                    return true;
                }
            }
            return false;
        }

        String output(final int repeat, final String id) {
            return repeat(repeat).get("outputs").path(id).asText("");
        }
    }

    @Test
    void judge_twoArmsOfOneDataset_gradesEveryChangedParagraph() throws IOException {
        final Map<String, String> env = System.getenv();
        final Side a = side(env, "BOOKLOOM_EVAL_JUDGE_A");
        final Side b = side(env, "BOOKLOOM_EVAL_JUDGE_B");
        final String dataset = a.report().at("/provenance/dataset/id").asText();
        assertThat(b.report().at("/provenance/dataset/id").asText()).isEqualTo(dataset);
        final MiniBook book = MiniBook.load(dataset);
        final String model = env.getOrDefault("BOOKLOOM_EVAL_MODEL", JudgeCalibrationEvalTest.DEFAULT_JUDGE);
        final JudgeRunner judge = new JudgeRunner(
                PromptEvalTest.chatModel(model),
                JudgeRunner.Setup.of(model, env.get("BOOKLOOM_EVAL_QUANT"), null),
                JudgeRubric.load(),
                JudgeRunner.defaultCacheDir());
        final ObjectNode out = MAPPER.createObjectNode().put("dataset", dataset).put("judge", model);
        out.put("a", a.label()).put("b", b.label());
        final int repeats = Math.min(a.repeats(), b.repeats());
        final ArrayNode across = out.putArray("across");
        final ArrayNode edits = out.putArray("edits");
        for (int repeat = 0; repeat < repeats; repeat++) {
            judgeRepeat(judge, book, a, b, repeat, across, edits);
        }
        final Path file =
                Path.of(Objects.requireNonNull(env.get("BOOKLOOM_EVAL_JUDGE_OUT"), "BOOKLOOM_EVAL_JUDGE_OUT"));
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        final String table = table(out);
        Files.writeString(Path.of(file.toString().replaceAll("\\.json$", "") + ".txt"), table);
        log.info("Proofread judge {} against {}\n{}", a.label(), b.label(), table);
    }

    private static Side side(final Map<String, String> env, final String name) throws IOException {
        final Path path = Path.of(Objects.requireNonNull(env.get(name), name));
        return new Side(
                MAPPER.readTree(Files.readString(path)),
                path.getParent().getFileName().toString());
    }

    private static void judgeRepeat(
            final JudgeRunner judge,
            final MiniBook book,
            final Side a,
            final Side b,
            final int repeat,
            final ArrayNode across,
            final ArrayNode edits) {
        final List<MiniBook.Block> blocks = book.blocks();
        for (int index = 0; index < blocks.size(); index++) {
            final String id = blocks.get(index).id();
            final boolean keptA = a.isKept(repeat, id);
            final boolean keptB = b.isKept(repeat, id);
            if (keptA || keptB) {
                final JudgeItem item = item(book, index, repeat);
                final PairVerdict verdict = judge.judgePair(item, a.output(repeat, id), b.output(repeat, id));
                across.addObject()
                        .put("id", id)
                        .put("repeat", repeat)
                        .put("keptA", keptA)
                        .put("keptB", keptB)
                        .put("verdict", verdict.name());
            }
        }
        edits(judge, book, a, "a", repeat, edits);
        edits(judge, book, b, "b", repeat, edits);
    }

    // Each kept rewrite of one side against the text it replaced: FIRST means the rewrite reads better.
    private static void edits(
            final JudgeRunner judge,
            final MiniBook book,
            final Side side,
            final String name,
            final int repeat,
            final ArrayNode edits) {
        for (final JsonNode read : side.repeat(repeat).get("proofread")) {
            final String id = read.get("id").asText();
            if (KEPT.equals(read.get("outcome").asText())) {
                final PairVerdict verdict = judge.judgePair(
                        item(book, indexOf(book, id), repeat),
                        read.get("after").asText(),
                        read.get("before").asText());
                edits.addObject()
                        .put("side", name)
                        .put("id", id)
                        .put("repeat", repeat)
                        .put("verdict", verdict.name());
            }
        }
    }

    private static int indexOf(final MiniBook book, final String id) {
        final List<String> ids = book.blocks().stream().map(MiniBook.Block::id).toList();
        return ids.indexOf(id);
    }

    private static JudgeItem item(final MiniBook book, final int index, final int repeat) {
        final List<MiniBook.Block> blocks = book.blocks();
        final List<String> neighbours = new ArrayList<>();
        if (index > 0) {
            neighbours.add(blocks.get(index - 1).text());
        }
        if (index + 1 < blocks.size()) {
            neighbours.add(blocks.get(index + 1).text());
        }
        return new JudgeItem(
                blocks.get(index).id() + "#" + repeat,
                book.sourceLanguage(),
                book.targetLanguage(),
                blocks.get(index).text(),
                List.of(),
                neighbours);
    }

    // A wins, B wins, ties and identical texts across the arms, then each side's rewrites better, worse and even.
    private static String table(final JsonNode out) {
        final StringBuilder text = new StringBuilder(String.format(
                Locale.ROOT,
                "%s judge=%s A=%s B=%s%n",
                out.get("dataset"),
                out.get("judge"),
                out.get("a"),
                out.get("b")));
        text.append(String.format(
                Locale.ROOT,
                "  changed paragraphs %d: A better %d, B better %d, tie %d, identical %d, unread %d%n",
                out.get("across").size(),
                count(out.get("across"), null, PairVerdict.FIRST),
                count(out.get("across"), null, PairVerdict.SECOND),
                count(out.get("across"), null, PairVerdict.TIE),
                count(out.get("across"), null, PairVerdict.IDENTICAL),
                count(out.get("across"), null, PairVerdict.UNPARSED)));
        for (final String side : List.of("a", "b")) {
            text.append(String.format(
                    Locale.ROOT,
                    "  %s kept rewrites: better %d, worse %d, tie %d, unread %d%n",
                    side.toUpperCase(Locale.ROOT),
                    count(out.get("edits"), side, PairVerdict.FIRST),
                    count(out.get("edits"), side, PairVerdict.SECOND),
                    count(out.get("edits"), side, PairVerdict.TIE),
                    count(out.get("edits"), side, PairVerdict.UNPARSED)));
        }
        return text.toString();
    }

    private static long count(final JsonNode rows, @Nullable final String side, final PairVerdict verdict) {
        long found = 0;
        for (final JsonNode row : rows) {
            final boolean ownSide = side == null || side.equals(row.path("side").asText());
            found += ownSide && verdict.name().equals(row.get("verdict").asText()) ? 1 : 0;
        }
        return found;
    }
}
