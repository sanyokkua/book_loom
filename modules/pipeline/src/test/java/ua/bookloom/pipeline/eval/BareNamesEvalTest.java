package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.eval.BareNames.BareName;
import ua.bookloom.pipeline.glossary.NameKnowledgeCalls;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * Arm 4a (task 7.8): the invented bare names of {@code eval/datasets/bare-names} asked through the production
 * name-knowledge stage ({@link NameKnowledgeCalls}), the English-looking names in an en-to-uk frame and the
 * Russian-looking ones in a ru-to-uk frame, as a book of each language would ask them, and scored by
 * {@link BareNamesScore}. Local-only: {@code BOOKLOOM_EVAL_SUITE=bare-names ./gradlew :pipeline:promptEval --tests
 * '*BareNamesEvalTest'} with {@code BOOKLOOM_EVAL_URL}, {@code BOOKLOOM_EVAL_MODEL} and {@code BOOKLOOM_EVAL_REPEATS}.
 * It asserts no floor; the report lands in {@code build/reports/promptEval/<model>-bare-names.json} and the history.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "bare-names")
class BareNamesEvalTest {

    private static final String TARGET = "uk";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void bareNames_realModel_scoresTheRule() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final List<BareName> names = BareNames.load();
        final EvalRepeats.Runs<Map<String, NameKnowledge>> runs = EvalRepeats.fromEnvironment(System.getenv())
                .run(
                        PromptEvalTest.chatModel(model),
                        seeded -> ask(seeded, names),
                        answers -> BareNamesScore.of(names, answers).passes() ? 1 : 0);
        final String table = table(model, names, runs);
        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + BareNames.DATASET;
        EvalOutput.write(
                EvalProvenance.capture(BareNames.DATASET, model, "")
                        .withDataset(BareNames.DATASET)
                        .withAggregates(runs.aggregates()),
                name,
                table,
                json(model, names, runs));
        log.info("Bare names report {}\n{}", name, table);
        assertThat(runs.results()).isNotEmpty();
    }

    // One call stream per language, each name as a character with no gender, so the stage asks every one of them.
    private static Map<String, NameKnowledge> ask(final ChatModel model, final List<BareName> names) {
        final StageProbe probe = new StageProbe(model);
        final NameKnowledgeCalls knowledge = new NameKnowledgeCalls(EvalVariants.templates(), MAPPER);
        final Map<String, NameKnowledge> byTerm = new LinkedHashMap<>();
        for (final String language : List.of("en", "ru")) {
            final List<GlossaryEntry> entries = names.stream()
                    .filter(name -> name.language().equals(language))
                    .map(name -> GlossaryEntry.of(
                            "bare:" + name.term(),
                            "bare",
                            name.term(),
                            null,
                            TermType.CHARACTER,
                            Gender.UNKNOWN,
                            false))
                    .toList();
            final CallFrame frame = new CallFrame(
                    language, TARGET, StyleSheet.from(BookBrief.defaults(language)), ForeignPassagePolicy.KEEP);
            final Result<Map<String, NameKnowledge>> answered = knowledge.ask(entries, frame, probe.calls(TARGET));
            assertThat(answered.isOk())
                    .as("name knowledge " + language + ": " + answered.error())
                    .isTrue();
            Objects.requireNonNull(answered.data(), "answers")
                    .forEach((id, answer) -> byTerm.put(id.substring("bare:".length()), answer));
        }
        log.info("Bare names answered {} of {} in {} calls", byTerm.size(), names.size(), probe.mark());
        return byTerm;
    }

    private static String table(
            final String model, final List<BareName> names, final EvalRepeats.Runs<Map<String, NameKnowledge>> runs) {
        final StringBuilder out =
                new StringBuilder(model + " bare names (rule: confident wrong <= 5 %, unisex < 0.7)\n");
        for (int repeat = 0; repeat < runs.results().size(); repeat++) {
            final BareNamesScore score = BareNamesScore.of(names, runs.results().get(repeat));
            out.append(String.format(
                    Locale.ROOT,
                    "seed %d: answered %d/%d, confident %d, wrong %d (%.1f %%), unisex >= 0.7: %d/%d, %s%n"
                            + "  wrong %s%n  unisex high %s%n",
                    runs.seeds().get(repeat),
                    score.answered(),
                    score.asked(),
                    score.confident(),
                    score.confidentWrong(),
                    100 * score.wrongShare(),
                    score.unisexConfident(),
                    score.unisex(),
                    score.passes() ? "PASS" : "FAIL",
                    score.wrongTerms(),
                    score.unisexConfidentTerms()));
        }
        return out.toString();
    }

    private static String json(
            final String model, final List<BareName> names, final EvalRepeats.Runs<Map<String, NameKnowledge>> runs) {
        final ObjectNode root =
                MAPPER.createObjectNode().put("suite", BareNames.DATASET).put("model", model);
        final ArrayNode repeats = root.putArray("repeats");
        for (int repeat = 0; repeat < runs.results().size(); repeat++) {
            final Map<String, NameKnowledge> answers = runs.results().get(repeat);
            final BareNamesScore score = BareNamesScore.of(names, answers);
            final ObjectNode row = repeats.addObject()
                    .put("seed", runs.seeds().get(repeat))
                    .put("answered", score.answered())
                    .put("confident", score.confident())
                    .put("confidentWrong", score.confidentWrong())
                    .put("wrongShare", score.wrongShare())
                    .put("unisex", score.unisex())
                    .put("unisexConfident", score.unisexConfident())
                    .put("passes", score.passes());
            final ArrayNode items = row.putArray("answers");
            answers.forEach((term, answer) -> items.addObject()
                    .put("term", term)
                    .put("gender", answer.gender().name())
                    .put("type", answer.type().name())
                    .put("confidence", answer.confidence()));
        }
        return root.toString();
    }
}
