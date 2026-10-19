package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The model-based validator against a scripted reply: only a word the text really holds becomes a finding. */
class ModelWordValidatorTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<String> TEXTS =
            List.of("Він швидко побіг до лісу.", "Він сидів на кафедрахрі й мовчки дивився.");

    private final List<ChatRequest> sent = new ArrayList<>();

    private ModelWordValidator answering(final Result<ChatResponse> reply) {
        final ModelCalls calls = (kind, segmentId, request) -> {
            sent.add(request);
            return reply;
        };
        return new ModelWordValidator(new PromptTemplates(), new ObjectMapper(), calls, FRAME);
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    @Test
    void findAll_wordTheTextHolds_becomesAFindingWithItsSpan() {
        final var found = answering(reply("{\"words\":[{\"id\":\"2\",\"word\":\"кафедрахрі\","
                        + "\"quote\":\"сидів на кафедрахрі й мовчки\"}]}"))
                .findAll(TEXTS);

        assertThat(found.get(0)).isEmpty();
        assertThat(found.get(1)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.UNKNOWN_WORD);
            assertThat(finding.blocking()).isFalse();
            assertThat(finding.span().text()).isEqualTo("кафедрахрі");
            assertThat(TEXTS.get(1)
                            .substring(finding.span().start(), finding.span().end()))
                    .isEqualTo("кафедрахрі");
        });
    }

    @Test
    void findAll_wordTheTextDoesNotHold_isDropped() {
        final var found = answering(reply("{\"words\":[{\"id\":\"1\",\"word\":\"лісфвуту\",\"quote\":\"\"}]}"))
                .findAll(TEXTS);

        assertThat(found).allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void findAll_wordInsideALongerWord_isDropped() {
        final var found = answering(reply("{\"words\":[{\"id\":\"1\",\"word\":\"біг\",\"quote\":\"\"}]}"))
                .findAll(TEXTS);

        assertThat(found.get(0)).isEmpty();
    }

    @Test
    void findAll_quoteTheTextDoesNotHold_isDropped() {
        final var found = answering(
                        reply("{\"words\":[{\"id\":\"2\",\"word\":\"кафедрахрі\"," + "\"quote\":\"сидів у кріслі\"}]}"))
                .findAll(TEXTS);

        assertThat(found.get(1)).isEmpty();
    }

    @Test
    void findAll_quoteWithoutTheWord_isDropped() {
        final var found = answering(reply(
                        "{\"words\":[{\"id\":\"2\",\"word\":\"кафедрахрі\"," + "\"quote\":\"й мовчки дивився\"}]}"))
                .findAll(TEXTS);

        assertThat(found.get(1)).isEmpty();
    }

    @Test
    void findAll_idOutsideTheBatchOrNotANumber_isDropped() {
        final var found = answering(reply("{\"words\":[{\"id\":\"7\",\"word\":\"кафедрахрі\",\"quote\":\"\"},"
                        + "{\"id\":\"x\",\"word\":\"кафедрахрі\",\"quote\":\"\"}]}"))
                .findAll(TEXTS);

        assertThat(found).allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void findAll_unreadableReply_saysNothing() {
        assertThat(answering(reply("I could not find any.")).findAll(TEXTS))
                .hasSize(2)
                .allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void findAll_failedCall_saysNothingInsteadOfBlocking() {
        final Result<ChatResponse> failed = Result.err(
                AppError.of(ErrorCode.timeout, "Timed out", "The model did not answer in time.", null, null));

        assertThat(answering(failed).findAll(TEXTS))
                .hasSize(2)
                .allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void findAll_anyBatch_isOneStructuredCallAtTemperatureZeroWithTheNumberedTexts() {
        answering(reply("{\"words\":[]}")).findAll(TEXTS);

        assertThat(sent).singleElement().satisfies(request -> {
            assertThat(request.temperature()).isZero();
            assertThat(request.callKind()).isEqualTo(CallKind.REVIEW);
            assertThat(Objects.requireNonNull(request.responseFormat()).name()).isEqualTo("suspicious-words");
            assertThat(request.messages().getLast().content())
                    .contains("1. Він швидко побіг до лісу.", "2. Він сидів на кафедрахрі й мовчки дивився.");
        });
    }

    @Test
    void find_singleText_isThatTextsFindings() {
        final var validator = answering(reply("{\"words\":[{\"id\":\"1\",\"word\":\"кафедрахрі\","
                + "\"quote\":\"сидів на кафедрахрі й мовчки\"}]}"));

        assertThat(validator.find(TEXTS.get(1), "uk"))
                .extracting(finding -> finding.span().text())
                .containsExactly("кафедрахрі");
    }

    @Test
    void forMode_model_buildsTheModelValidatorAndAnythingElseNone() {
        final ModelCalls calls = (kind, segmentId, request) -> reply("{\"words\":[]}");
        final PromptTemplates templates = new PromptTemplates();

        assertThat(WordValidators.forMode("MODEL", templates, new ObjectMapper(), calls, FRAME))
                .isInstanceOf(ModelWordValidator.class);
        assertThat(WordValidators.forMode(null, templates, new ObjectMapper(), calls, FRAME))
                .isSameAs(WordValidator.none());
        assertThat(WordValidators.forMode("none", templates, new ObjectMapper(), calls, FRAME))
                .isSameAs(WordValidator.none());
    }
}
