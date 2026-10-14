package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The long-paragraph suite (15i.X3) drafts the twenty paragraphs on the real draft path and keeps each source and
 * target for grading; whether a paragraph goes in groups follows the switch, as it does in a run.
 */
class LongParagraphEvalRunnerTest {

    private static final Pattern TEXT = Pattern.compile("<Text>\n(.*)\n</Text>", Pattern.DOTALL);
    private static final String PROPERTY = "bookloom.long.paragraphs";

    @AfterEach
    void clearSwitch() {
        System.clearProperty(PROPERTY);
    }

    // The model upper-cases the text it is shown, so each target is the paragraph's upper case when the groups join.
    private static ModelCalls uppercasing() {
        return (kind, segmentId, request) -> {
            final Matcher matcher = TEXT.matcher(request.messages().get(1).content());
            final String body = matcher.find() ? matcher.group(1) : "";
            return Result.ok(new ChatResponse(targetReply(body.toUpperCase(Locale.ROOT)), FinishReason.STOP));
        };
    }

    private static String targetReply(final String target) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of("target", target));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void run_switchOff_draftsEachOfTheTwentyParagraphsInOneCall() {
        final LongParagraphReport report = new LongParagraphEvalRunner(uppercasing()).run(LongParagraph.load());

        assertThat(report.rows()).hasSize(20);
        assertThat(report.rows()).allSatisfy(row -> assertThat(row.calls()).isEqualTo(1));
        assertThat(report.groups()).isFalse();
    }

    @Test
    void run_switchOn_draftsEachParagraphInSeveralCallsAndKeepsSourceAndTarget() {
        System.setProperty(PROPERTY, "groups");

        final LongParagraphReport report = new LongParagraphEvalRunner(uppercasing()).run(LongParagraph.load());

        assertThat(report.groups()).isTrue();
        assertThat(report.rows()).hasSize(20);
        assertThat(report.rows()).allSatisfy(row -> {
            assertThat(row.calls()).isGreaterThanOrEqualTo(3);
            assertThat(row.target()).isEqualTo(row.source().toUpperCase(Locale.ROOT));
        });
    }

    @Test
    void json_report_carriesTheSuiteTheSwitchAndEveryPair() throws IOException {
        final LongParagraphReport report = new LongParagraphEvalRunner(uppercasing()).run(LongParagraph.load());

        final JsonNode json = new ObjectMapper().readTree(report.json("scripted"));

        assertThat(json.get("suite").asText()).isEqualTo("longpara");
        assertThat(json.get("model").asText()).isEqualTo("scripted");
        assertThat(json.get("groups").asBoolean()).isFalse();
        assertThat(json.get("paragraphs")).hasSize(20);
        assertThat(json.get("paragraphs").get(0).get("id").asText()).isEqualTo("lp-01");
        assertThat(json.get("paragraphs").get(0).get("source").asText()).startsWith("About half-past nine");
        assertThat(json.get("paragraphs").get(0).get("target").asText()).startsWith("ABOUT HALF-PAST NINE");
    }

    @Test
    void table_report_hasALinePerParagraphAndATotal() {
        final LongParagraphReport report = new LongParagraphEvalRunner(uppercasing()).run(LongParagraph.load());

        final List<String> lines = report.table("scripted").lines().toList();

        assertThat(lines).hasSize(22);
        assertThat(lines.get(1)).contains("lp-01").contains("187");
        assertThat(lines.getLast()).contains("20 paragraphs").contains("20 calls");
    }
}
