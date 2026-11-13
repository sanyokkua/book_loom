package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.eval.JudgeReply.Scored;

class JudgeReplyParserTest {

    private static final String TRANSLATION = "Вона не знала, і не спитала б. Пелл мовчав біля лампи.";

    private static String recorded(final String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                JudgeReplyParserTest.class.getResourceAsStream("/eval/judge/recorded/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "pointwise-well-formed.json | scored meaning=4 grammar=3 terminology=3 fluency=4 errors=2 mqm=6"
                        + " verdict=major-issues",
                "pointwise-span-not-substring.json | scored meaning=5 grammar=4 terminology=5 fluency=4 errors=1 mqm=1"
                        + " verdict=minor-issues",
                "pointwise-score-seven.json | unparsed",
                "pointwise-cut-after-errors.txt | unparsed",
                "pointwise-inside-think.txt | scored meaning=5 grammar=5 terminology=5 fluency=5 errors=0 mqm=0"
                        + " verdict=good",
                "empty.txt | unparsed",
                "pairwise-a.json | unparsed",
            })
    void parsePointwise_recordedReply_isReadAsRecorded(final String file, final String expected) throws IOException {
        assertThat(JudgeReplyParser.parsePointwise(recorded(file), TRANSLATION).describe())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "pairwise-a.json | winner=A",
                "pairwise-b.json | winner=B",
                "empty.txt | unparsed",
                "pointwise-well-formed.json | unparsed",
                "pointwise-cut-after-errors.txt | unparsed",
            })
    void parsePairwise_recordedReply_isReadAsRecorded(final String file, final String expected) throws IOException {
        assertThat(JudgeReplyParser.parsePairwise(recorded(file)).describe()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"{\"winner\":\"tie\"}, winner=tie", "{\"winner\":\"TIE\"}, winner=tie", "{\"winner\":\"C\"}, unparsed"})
    void parsePairwise_winnerWord_isReadCaseInsensitively(final String reply, final String expected) {
        assertThat(JudgeReplyParser.parsePairwise(reply).describe()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "\"grammar\":4.5 | unparsed",
                "\"grammar\":\"4\" | unparsed",
                "\"grammar\":-1 | unparsed",
                "\"grammar\":0 | scored meaning=5 grammar=0 terminology=5 fluency=5 errors=0 mqm=0 verdict=good",
            })
    void parsePointwise_scoreValue_isNeverClamped(final String grammar, final String expected) {
        final String reply = "{\"analysis\":\"x\",\"errors\":[],\"scores\":{\"meaning\":5," + grammar
                + ",\"terminology\":5,\"fluency\":5},\"verdict\":\"good\"}";

        assertThat(JudgeReplyParser.parsePointwise(reply, TRANSLATION).describe())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"minor, 1", "major, 5", "critical, 25", "CRITICAL, 25"})
    void parsePointwise_oneErrorOfASeverity_costsItsMqmWeight(final String severity, final int penalty) {
        final String reply = "{\"analysis\":\"x\",\"errors\":[{\"span\":\"лампи\",\"severity\":\"" + severity
                + "\",\"class\":\"meaning\"}],\"scores\":{\"meaning\":2,\"grammar\":5,\"terminology\":5,"
                + "\"fluency\":5},\"flags\":{\"meaningError\":true},\"verdict\":\"major-issues\"}";

        final Scored scored = (Scored) JudgeReplyParser.parsePointwise(reply, TRANSLATION);

        assertThat(scored.mqmPenalty()).isEqualTo(penalty);
        assertThat(scored.flags().meaningError()).isTrue();
        assertThat(scored.flags().omission()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"fatal", "''"})
    void parsePointwise_unknownSeverity_dropsTheError(final String severity) {
        final String reply = "{\"errors\":[{\"span\":\"лампи\",\"severity\":\"" + severity
                + "\",\"class\":\"meaning\"}],\"scores\":{\"meaning\":2,\"grammar\":5,\"terminology\":5,"
                + "\"fluency\":5},\"verdict\":\"major-issues\"}";

        final Scored scored = (Scored) JudgeReplyParser.parsePointwise(reply, TRANSLATION);

        assertThat(scored.errors()).isEmpty();
    }
}
