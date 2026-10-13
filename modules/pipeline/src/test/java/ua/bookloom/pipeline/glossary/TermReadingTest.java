package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReading.Subject;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.index.WindowPlan;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The term reading's call (15i.N4): one term per call, its numbered windows with the term marked in each, the code's
 * pronoun tally and the person's other spellings on the term's line, sampled at zero with an output cap that grows with
 * the windows, and the reply read against the windows shown.
 */
class TermReadingTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final PronounGender.Evidence HE_FOUR =
            new PronounGender.Evidence(List.of(), List.of("he", "he", "he", "he"));
    private static final PronounGender.Evidence NONE = new PronounGender.Evidence(List.of(), List.of());
    private static final String EMPTY_READING = "{\"windows\":[],\"speaking\":false,\"brand\":false}";

    private final TermReading reading = new TermReading(new PromptTemplates(), new ObjectMapper());

    /** The bartender's book: two mentions of Mark, a paragraph apart, and a common "mark" the reading never shows. */
    private static List<Window> markWindows() {
        final BookIndex index = GlossaryTestSegments.of(List.of(
                        "Mark poured the beer.",
                        "Lorna watched him.",
                        "Then Mark wiped the bar.",
                        "She left a mark on the glass."))
                .index();
        return index.windows(index.mentions(List.of("Mark"), Case.SENSITIVE), new WindowPlan(4, 1, 350));
    }

    /** Windows cut by hand, one sentence each. */
    private static List<Window> windows(final int count) {
        return IntStream.range(0, count)
                .mapToObj(ordinal ->
                        new Window(new Mention("Ivo", ordinal, 0, 3, 0, true), ordinal, ordinal, "Ivo ran.", 0, 3))
                .toList();
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }

    private static ModelCalls calls(final ScriptedChatModel model, final List<CallKind> kinds) {
        return (kind, segmentId, request) -> {
            kinds.add(kind);
            return model.chat(request);
        };
    }

    private static String user(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    private Result<Reading> read(final Subject subject, final List<Window> windows, final ScriptedChatModel model) {
        return reading.read(subject, windows, FRAME, calls(model, new ArrayList<>()));
    }

    // IF several terms shared a call, THEN a small model would mix their windows: each call reads one term, as the
    // TERM_READING kind, sampled at zero so the same window is read the same way twice.
    @Test
    void read_oneTerm_sendsOneTermReadingCallAtTemperatureZero() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(EMPTY_READING));
        final List<CallKind> kinds = new ArrayList<>();

        reading.read(new Subject("Mark", 3, HE_FOUR, List.of()), markWindows(), FRAME, calls(model, kinds));

        assertThat(kinds).containsExactly(CallKind.TERM_READING);
        assertThat(model.requests()).hasSize(1);
        final ChatRequest request = model.requests().getFirst();
        assertThat(request.callKind()).isEqualTo(CallKind.TERM_READING);
        assertThat(request.temperature()).isEqualTo(0.0);
        assertThat(Objects.requireNonNull(request.responseFormat()).name()).isEqualTo("term-reading");
        assertThat(request.messages()).extracting(ChatMessage::role).containsExactly(ChatRole.SYSTEM, ChatRole.USER);
    }

    // Each window is numbered and the term is marked where the window was cut around it; the common "mark" of the
    // fourth paragraph is never a window. A window keeps its paragraphs on lines of their own, so the model sees where
    // a speaker's line starts.
    @Test
    void read_windows_areNumberedWithTheTermMarked() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(EMPTY_READING));

        read(new Subject("Mark", 2, HE_FOUR, List.of()), markWindows(), model);

        assertThat(user(model.requests().getFirst()))
                .contains("[1] [[Mark]] poured the beer.\nLorna watched him.")
                .contains("[2] Lorna watched him.\nThen [[Mark]] wiped the bar.\nShe left a mark on the glass.")
                .doesNotContain("[3]");
    }

    // The term's line carries what the code knows: how often the story writes it, the pronouns the code counted after
    // it, and the person's other spellings, so the model reads the windows against them.
    @Test
    void read_termLine_carriesTheCountTheTallyAndTheCluster() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(EMPTY_READING));

        read(new Subject("Mark", 12, HE_FOUR, List.of("Mark Ellery", "Ellery")), markWindows(), model);

        assertThat(user(model.requests().getFirst())).contains("Mark", "12", "he ×4", "Mark Ellery", "Ellery");
    }

    @Test
    void read_termWithNoTallyAndNoCluster_showsNeitherCountNorOtherSpellings() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(EMPTY_READING));

        read(new Subject("Mark", 2, NONE, List.of()), markWindows(), model);

        assertThat(user(model.requests().getFirst()))
                .doesNotContain("×", "Ellery")
                .doesNotContain("{{", "}}");
    }

    // The reply is per window a quote and three short labels: about 80 tokens a window and 40 for the flags, and the
    // cap is twice that, so a full answer is never cut but a loop is.
    @ParameterizedTest(name = "{0} windows -> expected {1}, cap {2}")
    @CsvSource({"1,120,240", "4,360,720", "6,520,1040"})
    void read_outputLimit_growsWithTheWindows(final int count, final int expected, final int cap) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(EMPTY_READING));

        read(new Subject("Ivo", count, NONE, List.of()), windows(count), model);

        assertThat(model.requests().getFirst())
                .extracting(ChatRequest::expectedOutputTokens, ChatRequest::maxOutputTokens)
                .containsExactly(expected, cap);
    }

    @Test
    void read_reply_isReadAgainstTheWindowsShown() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("""
                        {"windows":[{"window":2,"quote":"Lorna watched him","usage":"name","kind":"person",\
                        "gender":"male"}],"speaking":false,"brand":false}"""));

        final Result<Reading> result = read(new Subject("Mark", 2, HE_FOUR, List.of()), markWindows(), model);

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).windows())
                .extracting(WindowReading::number, WindowReading::vote)
                .containsExactly(tuple(2, Gender.MALE));
    }

    @Test
    void read_callFails_returnsTheErrorAndNoReading() {
        final AppError refused = AppError.of(ErrorCode.unreachable, "Down", "The model is not running.");
        final ScriptedChatModel model = new ScriptedChatModel().answer(Result.err(refused));

        final Result<Reading> result = read(new Subject("Mark", 2, HE_FOUR, List.of()), markWindows(), model);

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void read_unreadableReply_isNoReading() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("I think Mark is a bartender."));

        final Result<Reading> result = read(new Subject("Mark", 2, HE_FOUR, List.of()), markWindows(), model);

        assertThat(result.data()).isEqualTo(Reading.none("Mark"));
    }

    // IF a reading went out with nothing to read, THEN the model would invent a usage: no window, no call.
    @Test
    void read_noWindows_makesNoCallAndReadsNothing() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<Reading> result = read(new Subject("Quill", 0, NONE, List.of()), List.of(), model);

        assertThat(model.requests()).isEmpty();
        assertThat(result.data()).isEqualTo(Reading.none("Quill"));
    }
}
