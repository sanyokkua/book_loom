package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.checks.ModelWordValidator;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The garbled-word corpus and its measurement, run with a scripted model so no provider is needed. */
class WordsEvalRunnerTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    @Test
    void corpus_isTenGarbledAndTenCleanWithTheWordsInTheText() {
        final List<WordCase> cases = WordsEvalTest.cases();

        assertThat(cases.stream().filter(WordCase::defective)).hasSize(10).allSatisfy(wordCase -> {
            assertThat(wordCase.kind()).isEqualTo("garbled");
            assertThat(wordCase.words())
                    .isNotEmpty()
                    .allSatisfy(word -> assertThat(wordCase.candidate()).contains(word));
        });
        assertThat(cases.stream().filter(wordCase -> !wordCase.defective()))
                .hasSize(10)
                .allSatisfy(wordCase -> assertThat(wordCase.words()).isEmpty());
        assertThat(cases.stream().flatMap(wordCase -> wordCase.words().stream()))
                .contains("кафедрахрі", "розлізяв", "дірявтиму");
    }

    @Test
    void run_modelThatNamesEveryGarbledWord_hasFullRecallAndNoFalseAlarm() {
        final List<WordCase> cases = WordsEvalTest.cases();
        final ModelCalls calls = (kind, segmentId, request) -> Result.ok(
                new ChatResponse(replyNaming(cases, request.messages().getLast().content()), FinishReason.STOP));

        final WordsEvalReport report = new WordsEvalReport(
                "scripted",
                new WordsEvalRunner(new ModelWordValidator(new PromptTemplates(), new ObjectMapper(), calls, FRAME), 5)
                        .run(cases));

        assertThat(report.recall()).isEqualTo(1.0);
        assertThat(report.falsePositiveRate()).isZero();
        assertThat(report.meetsTarget()).isTrue();
        assertThat(report.json()).contains("\"suite\":\"words\"", "\"recall\":1.000");
    }

    @Test
    void report_modelThatSaysNothing_hasNoRecall() {
        final List<WordsEvalRow> rows = WordsEvalTest.cases().stream()
                .map(wordCase -> new WordsEvalRow(wordCase.id(), wordCase.defective(), wordCase.words(), List.of()))
                .toList();

        assertThat(new WordsEvalReport("silent", rows).recall()).isZero();
        assertThat(new WordsEvalReport("silent", rows).meetsTarget()).isFalse();
    }

    // Answers like a perfect model: every garbled word of the numbered texts it was shown.
    private static String replyNaming(final List<WordCase> cases, final String user) {
        final StringBuilder entries = new StringBuilder();
        for (final WordCase wordCase : cases) {
            if (wordCase.defective() && user.contains(wordCase.candidate())) {
                final String number = user.lines()
                        .filter(line -> line.endsWith(wordCase.candidate()))
                        .map(line -> line.substring(0, line.indexOf('.')))
                        .findFirst()
                        .orElseThrow();
                entries.append(entries.isEmpty() ? "" : ",");
                entries.append("{\"id\":\"%s\",\"word\":\"%s\",\"quote\":\"\"}"
                        .formatted(number, wordCase.words().getFirst()));
            }
        }
        return "{\"words\":[" + entries + "]}";
    }
}
