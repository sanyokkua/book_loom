package ua.bookloom.llm.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ModelProfile.MarkupFilter;

/** Every known reasoning and turn marker is removed, and the two profile filters run only where switched on. */
class ReasoningScrubberTest {

    private static final String ANSWER = "{\"items\":[]}";

    @ParameterizedTest(name = "{0}")
    @MethodSource("markers")
    void scrub_eachKnownMarker_isRemoved(final String marker, final String reply) {
        assertThat(ReasoningScrubber.scrub(reply)).isEqualTo(ANSWER);
    }

    private static Stream<Arguments> markers() {
        return Stream.of(
                arguments("think tags", "<think>The word is greeting.</think>" + ANSWER),
                arguments("thinking tags in any case", "<THINKING>private</thinking>" + ANSWER),
                arguments("reasoning tags", "<reasoning>private</reasoning>" + ANSWER),
                arguments("bracket THINK", "[THINK]Let me see.[/THINK]" + ANSWER),
                arguments("closer with no opener", "Okay, the user wants Ukrainian.</think>" + ANSWER),
                arguments("unclosed opener", ANSWER + "<THINKING>and then"),
                arguments(
                        "harmony analysis channel",
                        "<|channel|>analysis<|message|>Translate it.<|end|><|start|>assistant<|channel|>final"
                                + "<|message|>" + ANSWER + "<|return|>"),
                arguments(
                        "harmony constrained final",
                        "<|channel|>final <|constrain|>json<|message|>" + ANSWER + "<|end|>"),
                arguments("gemma channel", "<|channel>thought plan<channel|>" + ANSWER + "<end_of_turn>"),
                arguments("bare gemma thought", "thought\n<channel|>" + ANSWER),
                arguments("leaked turn tokens", "<start_of_turn>model" + ANSWER + "<|im_end|>"),
                arguments("fenced json", "```json\n" + ANSWER + "\n```"),
                arguments("bare json label", "json" + ANSWER));
    }

    // A reply cut off while it was thinking opens with the marker; the bracket and reasoning openers count only there.
    @ParameterizedTest
    @ValueSource(strings = {"[THINK]and then", "  <reasoning>first I will", "<|channel>thought plan"})
    void scrub_replyOpenedByAnUnclosedMarker_isEmpty(final String reply) {
        assertThat(ReasoningScrubber.scrub(reply)).isEmpty();
        assertThat(ReasoningScrubber.hasUnclosedOpener(reply)).isTrue();
    }

    // A plain model's translation may hold the words literally; nothing after them is cut.
    @ParameterizedTest
    @ValueSource(strings = {"Він написав [think] на дошці.", "Слово <reasoning> стоїть у дужках."})
    void scrub_bracketOrReasoningOpenerMidText_isKept(final String reply) {
        assertThat(ReasoningScrubber.scrub(reply)).isEqualTo(reply);
        assertThat(ReasoningScrubber.hasUnclosedOpener(reply)).isFalse();
    }

    @Test
    void scrub_onlyReasoning_isEmpty() {
        assertThat(ReasoningScrubber.scrub("<think>nothing to say</think>")).isEmpty();
    }

    @Test
    void scrub_textAroundABlock_keepsBothSides() {
        assertThat(ReasoningScrubber.scrub("Before <think>private</think> after"))
                .isEqualTo("Before  after");
    }

    @Test
    void scrub_boxedWithTheFilter_isUnwrapped() {
        assertThat(ReasoningScrubber.scrub("\\boxed{Привіт}", Set.of(MarkupFilter.BOXED)))
                .isEqualTo("Привіт");
    }

    @Test
    void scrub_boxedInsideAJsonString_isUnwrapped() {
        assertThat(ReasoningScrubber.scrub("{\"target\":\"\\\\boxed{Привіт}\"}", Set.of(MarkupFilter.BOXED)))
                .isEqualTo("{\"target\":\"Привіт\"}");
    }

    @Test
    void scrub_boxedWithoutTheFilter_isKept() {
        assertThat(ReasoningScrubber.scrub("\\boxed{Привіт}")).isEqualTo("\\boxed{Привіт}");
    }

    @Test
    void scrub_toSelfWithTheFilter_keepsOnlyTheAnswer() {
        assertThat(ReasoningScrubber.scrub(
                        "to=self<|message|>plan the reply<|eot|><|start|>assistant to=user<|message|>" + ANSWER
                                + "<|eot|>",
                        Set.of(MarkupFilter.TO_SELF)))
                .isEqualTo(ANSWER);
    }

    @Test
    void scrub_toSelfWithoutTheFilter_isKept() {
        assertThat(ReasoningScrubber.scrub("to=self<|message|>note")).isEqualTo("to=self<|message|>note");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<think>first I will", "[THINK]first", "<reasoning>and", "answer<thinking>and"})
    void hasUnclosedOpener_openerNeverClosed_isTrue(final String reply) {
        assertThat(ReasoningScrubber.hasUnclosedOpener(reply)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<think>done</think>text", "plain text", "[THINK]a[/THINK]b"})
    void hasUnclosedOpener_closedOrNone_isFalse(final String reply) {
        assertThat(ReasoningScrubber.hasUnclosedOpener(reply)).isFalse();
    }
}
