package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind.Level;

/** One record per case and repeat: missing figures stay missing, and the calls go to a file beside the record. */
class CaseRecordTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ChatRequest REQUEST = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate.")));

    private static CaseRecord record(final List<StageProbe.Call> calls) {
        return new CaseRecord(
                "ch02.md:14",
                1,
                1001,
                List.of(new CaseRecord.Grader("NUMBERS_KEPT", Level.HARD, true)),
                CaseRecord.Time.of(calls),
                CaseRecord.Tokens.of(calls));
    }

    @Test
    void json_missingTimeFigure_isNullNotZero() throws IOException {
        // The provider reported the generation time and the counts, but not the prompt reading time or a cache figure.
        final TokenUsage usage = new TokenUsage(120, 30, Duration.ofMillis(900));

        final JsonNode json = MAPPER.readTree(record(List.of(new StageProbe.Call(REQUEST, false, "ok", usage)))
                .json());

        assertThat(json.at("/time/generation").asLong()).isEqualTo(900);
        assertThat(json.at("/time/promptRead").isNull()).isTrue();
        assertThat(json.at("/time/ttft").isNull()).isTrue();
        assertThat(json.at("/time/total").isNull()).isTrue();
        assertThat(json.at("/tokens/prompt").asInt()).isEqualTo(120);
        assertThat(json.at("/tokens/cached").isNull()).isTrue();
        assertThat(json.at("/graders/0/level").asText()).isEqualTo("HARD");
        assertThat(json.at("/replyRef").asText()).isEqualTo("calls/ch02.md_14-1.json");
    }

    @Test
    void write_oneCase_writesOneJsonLineAndOneCallsFile(@TempDir final Path run) throws IOException {
        final CaseRecord record = record(List.of());

        EvalOutput.writeCase(
                run,
                record,
                List.of(CaseRecord.Exchange.of(new StageProbe.Call(REQUEST, false, "Перекладено.", null))));

        assertThat(Files.readAllLines(run.resolve("cases.jsonl"), StandardCharsets.UTF_8))
                .singleElement()
                .satisfies(line ->
                        assertThat(MAPPER.readTree(line).get("caseId").asText()).isEqualTo("ch02.md:14"));
        final JsonNode calls = MAPPER.readTree(Files.readString(run.resolve("calls/ch02.md_14-1.json")));
        assertThat(calls.at("/0/reply").asText()).isEqualTo("Перекладено.");
    }
}
