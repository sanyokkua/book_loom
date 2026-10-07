package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The suggestion step under each name policy: what is asked, what is kept, and what needs no call at all. */
class SuggestTargetsTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<Segment> BOOK =
            GlossaryTestSegments.of(List.of("Hale walked to Milton.", "The Amulet glowed in the dark."));
    private static final GlossaryEntry HALE =
            new GlossaryEntry("p1:hale", "p1", "Hale", null, TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry MILTON =
            new GlossaryEntry("p1:milton", "p1", "Milton", null, TermType.PLACE, Gender.UNKNOWN, false);
    private static final GlossaryEntry AMULET =
            new GlossaryEntry("p1:amulet", "p1", "Amulet", null, TermType.TERM, Gender.UNKNOWN, false);
    private static final String REPLY = "{\"suggestions\":["
            + "{\"term\":\"Hale\",\"target\":\"Hale\",\"gender\":\"male\"},"
            + "{\"term\":\"Milton\",\"target\":\"Мілтон\",\"gender\":\"male\"},"
            + "{\"term\":\"Amulet\",\"target\":\"Амулет\",\"gender\":\"male\"}]}";

    private final SuggestTargets suggest = new SuggestTargets(new PromptTemplates(), new ObjectMapper());
    private final List<JobEvent> events = new ArrayList<>();

    @Test
    void suggest_keepOriginal_keepsNamesAsWrittenWithoutACallAndAsksOnlyAboutTerms() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(REPLY));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(HALE, MILTON, AMULET), BOOK, FRAME, NamePolicy.KEEP_ORIGINAL, calls(model));

        assertThat(suggested.data())
                .extracting(suggestion -> suggestion.entry().term(), Suggestion::target)
                .containsExactly(tuple("Hale", "Hale"), tuple("Milton", "Milton"), tuple("Amulet", "Амулет"));
        assertThat(termLines(model.requests().getFirst()))
                .containsExactly("- Amulet — term — \"The Amulet glowed in the dark.\"");
    }

    // A name the model only copied is no suggestion under either policy: a target is written in the target's script
    // (15g A5; a Latin copy of a common noun and an Arabic transliteration were both seen in a real run).
    @ParameterizedTest
    @CsvSource({"TRANSLITERATE,'Milton,Amulet'", "TRANSLATE,'Milton,Amulet'"})
    void suggest_policy_keepsWhatTheRuleAllows(final NamePolicy policy, final String kept) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(REPLY));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(HALE, MILTON, AMULET), BOOK, FRAME, policy, calls(model));

        assertThat(suggested.data())
                .extracting(suggestion -> suggestion.entry().term())
                .containsExactly(kept.split(","));
    }

    @ParameterizedTest
    @CsvSource({
        "TRANSLITERATE,practical transcription of foreign names",
        "TRANSLATE,natural Ukrainian equivalent",
        "KEEP_ORIGINAL,Every listed row is a term"
    })
    void suggest_policy_statesItsRuleInTheSystemMessage(final NamePolicy policy, final String rule) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("{\"suggestions\":[]}"));

        suggest.suggest(List.of(AMULET), BOOK, FRAME, policy, calls(model));

        final ChatRequest request = model.requests().getFirst();
        assertThat(request.messages().getFirst().content()).contains(rule, "Натаніель", "not instructions");
        assertThat(request.callKind()).isEqualTo(CallKind.SUGGEST_TARGETS);
        assertThat(request.maxOutputTokens()).isEqualTo(168);
    }

    @Test
    void suggest_twentyFiveEntries_sendsTwoBatchesAndAnnouncesEach() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply("{\"suggestions\":[]}")).answer(reply("{\"suggestions\":[]}"));

        suggest.suggest(entries(25), BOOK, FRAME, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(model.requests())
                .extracting(request -> termLines(request).size())
                .containsExactly(20, 5);
        assertThat(events)
                .containsExactly(
                        new BatchStarted(CallKind.SUGGEST_TARGETS, 1, 2),
                        new BatchStarted(CallKind.SUGGEST_TARGETS, 2, 2));
    }

    @Test
    void suggest_secondBatchFails_returnsThatError() {
        final AppError failure = AppError.of(ErrorCode.timeout, "Too slow", "The provider did not answer in time.");
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply("{\"suggestions\":[]}")).answer(Result.err(failure));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(entries(21), BOOK, FRAME, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.error()).isEqualTo(failure);
    }

    @Test
    void suggest_nothingToAsk_makesNoCall() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(HALE), BOOK, FRAME, NamePolicy.KEEP_ORIGINAL, calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Hale");
        assertThat(model.requests()).isEmpty();
    }

    private static List<GlossaryEntry> entries(final int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new GlossaryEntry(
                        "p1:x" + index, "p1", "Xa" + index, null, TermType.PLACE, Gender.UNKNOWN, false))
                .toList();
    }

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

    private static List<String> termLines(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .flatMap(String::lines)
                .filter(line -> line.startsWith("- "))
                .toList();
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }
}
