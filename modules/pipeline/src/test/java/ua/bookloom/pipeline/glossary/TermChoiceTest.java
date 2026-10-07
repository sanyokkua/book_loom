package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The model's keep-or-drop choice among recurring words: what it is shown, and what is taken from its answer. */
class TermChoiceTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<Segment> BOOK = GlossaryTestSegments.of(List.of(
            "The master drew the pentacle on the floor.",
            "A pentacle needs a circle.",
            "She put the table by the door."));

    private final TermChoice choice = new TermChoice(new PromptTemplates(), new ObjectMapper());
    private final List<JobEvent> events = new ArrayList<>();

    private ModelCalls calls(final ScriptedChatModel model) {
        return new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                return model.chat(request);
            }

            @Override
            public void announce(final JobEvent event) {
                events.add(event);
            }
        };
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }

    // IF the model were not shown how the book uses a word, THEN it could not tell a term from an ordinary word.
    @Test
    void choose_candidates_areShownWithTheirUsesAndAnExampleAndTheKeptOnesComeBack() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply(
                        "{\"terms\":[{\"term\":\"pentacle\",\"keep\":true},{\"term\":\"table\",\"keep\":false}]}"));

        final Result<Set<String>> kept = choice.choose(List.of("pentacle", "table"), BOOK, FRAME, calls(model));

        assertThat(kept.data()).containsExactly("pentacle");
        final String user = model.requests().getFirst().messages().get(1).content();
        assertThat(user).contains("- pentacle · 2 uses — \"The master drew the pentacle on the floor.\"");
        assertThat(model.requests().getFirst().callKind()).isEqualTo(CallKind.REVIEW_TERMS);
    }

    // IF the model could add words of its own, THEN the lexicon would fill with whatever it invented.
    @Test
    void choose_modelNamesAWordNobodyAsked_isNotKept() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply(
                        "{\"terms\":[{\"term\":\"dragon\",\"keep\":true},{\"term\":\"pentacle\",\"keep\":true}]}"));

        assertThat(choice.choose(List.of("pentacle"), BOOK, FRAME, calls(model)).data())
                .containsExactly("pentacle");
    }

    @Test
    void choose_replyThatIsNotJson_keepsNothing() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("They are all terms."));

        assertThat(choice.choose(List.of("pentacle"), BOOK, FRAME, calls(model)).data())
                .isEmpty();
    }

    @Test
    void choose_fiftyCandidates_areSentInTwoBatchesAndEachAnnounced() {
        final List<String> terms =
                IntStream.range(0, 50).mapToObj(n -> "word" + n).toList();
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply("{\"terms\":[]}")).answer(reply("{\"terms\":[]}"));

        choice.choose(terms, BOOK, FRAME, calls(model));

        assertThat(model.requests()).hasSize(2);
        assertThat(events)
                .containsExactly(
                        new BatchStarted(CallKind.REVIEW_TERMS, 1, 2), new BatchStarted(CallKind.REVIEW_TERMS, 2, 2));
    }

    @Test
    void choose_failedCall_endsWithItsErrorAndKeepsNothing() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Down", "The server is not answering.")));

        final Result<Set<String>> kept = choice.choose(List.of("pentacle"), BOOK, FRAME, calls(model));

        assertThat(kept.error()).isNotNull();
        assertThat(kept.error().code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void choose_noCandidates_asksNothing() {
        final ScriptedChatModel model = new ScriptedChatModel();

        assertThat(choice.choose(List.of(), BOOK, FRAME, calls(model)).data()).isEmpty();
        assertThat(model.requests()).isEmpty();
    }
}
