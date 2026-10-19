package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Decision;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReading.Usage;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The call the verdict may add to a term's readings (15i.N5): at Max one {@code term-summary} call, under the
 * {@link CallKind#TERM_READING} kind, when a term's reading calls disagree. Every other decision costs no call, a
 * majority against the code's tally included (no model's votes passed the coreference gate, so the tally stands). The
 * model is scripted; the text is invented.
 */
class ReadingVerdictCallsTest {

    private static final String PROJECT = "p1";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String SUMMARY = "term-summary";
    private static final String HE_TEXT = "Brann waved from the cart. He left before noon.";
    private static final String SHE_TEXT = "Brann waved from the cart. She left before noon.";
    private static final String CODE_DOG = "Brann sat down on the step. She smiled at the dog.";
    private static final String CODE_LAMP = "Brann woke before the others. She lit the lamp.";
    private static final String PERSON_TEXT = "Brann bowed to the crowd at the gate.";
    private static final String PLACE_TEXT = "They rode into Brann at dusk, tired and wet.";

    private final ReadingVerdict verdicts = new ReadingVerdict(new PromptTemplates(), new ObjectMapper());
    private final List<CallKind> kinds = new ArrayList<>();

    /** One window as it was shown and as the reading kept its answer. */
    private record Answer(String text, Usage usage, TermReading.Kind kind, String quote, Gender vote) {}

    private static Answer he() {
        return new Answer(HE_TEXT, Usage.NAME, TermReading.Kind.PERSON, "He left before noon", Gender.MALE);
    }

    private static Answer she() {
        return new Answer(SHE_TEXT, Usage.NAME, TermReading.Kind.PERSON, "She left before noon", Gender.FEMALE);
    }

    private static Answer person() {
        return new Answer(PERSON_TEXT, Usage.NAME, TermReading.Kind.PERSON, "", Gender.UNKNOWN);
    }

    private static Answer place() {
        return new Answer(PLACE_TEXT, Usage.NAME, TermReading.Kind.PLACE, "", Gender.UNKNOWN);
    }

    private static List<Answer> times(final int count, final Answer answer) {
        return Collections.nCopies(count, answer);
    }

    @SafeVarargs
    private static List<Answer> answers(final List<Answer>... parts) {
        final List<Answer> all = new ArrayList<>();
        for (final List<Answer> part : parts) {
            all.addAll(part);
        }
        return all;
    }

    private static ReadCall call(final List<Answer> answers) {
        final List<Window> windows = IntStream.range(0, answers.size())
                .mapToObj(index -> new Window(
                        new Mention("Brann", index, 0, 5, 0, true),
                        index,
                        index,
                        answers.get(index).text(),
                        0,
                        5))
                .toList();
        final List<WindowReading> readings = IntStream.range(0, answers.size())
                .mapToObj(index -> {
                    final Answer answer = answers.get(index);
                    return new WindowReading(
                            index + 1, answer.quote(), answer.usage(), answer.kind(), answer.vote(), answer.vote());
                })
                .toList();
        return new ReadCall(windows, new Reading("Brann", readings, false, false));
    }

    private static PronounGender.Evidence tally(final int she, final int he) {
        return new PronounGender.Evidence(Collections.nCopies(she, "she"), Collections.nCopies(he, "he"));
    }

    private static GlossaryEntry untyped() {
        return new GlossaryEntry("p1:Brann", PROJECT, "Brann", null, TermType.OTHER, Gender.UNKNOWN, false);
    }

    /** The code's side: two review windows in which the code reads "she" after the name, and a tally of she ×4. */
    private static NameWindows.Evidence sheEvidence() {
        return new NameWindows.Evidence(
                8,
                List.of(
                        new NameWindows.Window(CODE_DOG, Gender.FEMALE),
                        new NameWindows.Window(CODE_LAMP, Gender.FEMALE)),
                tally(4, 0));
    }

    /** Three verified "he" votes in one call against the code's she ×4. */
    private static Facts tiebreakCase(final GlossaryEntry entry) {
        return new Facts(
                entry,
                List.of(call(answers(times(3, he()), List.of(person())))),
                List.of(),
                sheEvidence(),
                0.0,
                QualityDial.BALANCED,
                "en");
    }

    /** Two reading calls of six windows each, the first reading a person and the second a place, or both a person. */
    private static Facts twoCalls(final QualityDial dial, final Answer second) {
        return new Facts(
                untyped(),
                List.of(call(times(6, person())), call(times(6, second))),
                List.of(),
                new NameWindows.Evidence(12, List.of(), tally(0, 0)),
                0.0,
                dial,
                "en");
    }

    private ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> {
            kinds.add(kind);
            return model.chat(request);
        };
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }

    private static String format(final ChatRequest request) {
        return Objects.requireNonNull(request.responseFormat(), "responseFormat")
                .name();
    }

    private static String user(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    private static Decision decided(final Result<Decision> result) {
        assertThat(result.isOk()).as("verdict %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "decision");
    }

    // --- no call ----------------------------------------------------------------------------------------------------

    // The same majority against the code's she ×4 costs no call, and the tally stands, as a suggestion, since no vote
    // agrees with it (15i.N gate).
    @Test
    void verdict_votesAgainstADominantTally_sendNoCallAndTheTallyStands() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Decision decision = decided(verdicts.verdict(tiebreakCase(untyped()), FRAME, calls(model)));

        assertThat(model.requests()).isEmpty();
        assertThat(decision.gender()).isEqualTo(Gender.FEMALE);
        assertThat(decision.origin()).isEqualTo(GenderOrigin.SUGGESTED);
    }

    @Test
    void verdict_votesAgreeWithTheTally_sendNoCall() {
        final ScriptedChatModel model = new ScriptedChatModel();
        final Facts agreeing = new Facts(
                untyped(),
                List.of(call(answers(times(2, she()), List.of(person())))),
                List.of(),
                sheEvidence(),
                0.0,
                QualityDial.BALANCED,
                "en");

        final Decision decision = decided(verdicts.verdict(agreeing, FRAME, calls(model)));

        assertThat(model.requests()).isEmpty();
        assertThat(decision.gender()).isEqualTo(Gender.FEMALE);
        assertThat(decision.origin()).isEqualTo(GenderOrigin.VERIFIED);
    }

    // A tally that favours the other gender at under 80 % only keeps the votes from being verified (15i.N1); it is no
    // tie worth a call.
    @Test
    void verdict_votesAgainstAFavouredButNotDominantTally_sendNoCall() {
        final ScriptedChatModel model = new ScriptedChatModel();
        final Facts favoured = new Facts(
                untyped(),
                List.of(call(answers(times(3, he()), List.of(person())))),
                List.of(),
                new NameWindows.Evidence(8, List.of(new NameWindows.Window(CODE_DOG, Gender.FEMALE)), tally(3, 2)),
                0.0,
                QualityDial.BALANCED,
                "en");

        decided(verdicts.verdict(favoured, FRAME, calls(model)));

        assertThat(model.requests()).isEmpty();
    }

    @Test
    void verdict_personSetGender_sendsNoCall() {
        final ScriptedChatModel model = new ScriptedChatModel();
        final GlossaryEntry person = untyped().withType(TermType.CHARACTER).withGender(Gender.MALE);

        decided(verdicts.verdict(tiebreakCase(person), FRAME, calls(model)));

        assertThat(model.requests()).isEmpty();
    }

    // --- the summary at Max ------------------------------------------------------------------------------------------

    // IF two calls that read the same term differently were simply pooled, THEN the larger call would win unseen: at
    // Max one summary call, of the term-reading kind, sees what each call read.
    @Test
    void verdict_maxAndTwoCallsDisagree_sendsOneSummaryShowingBothCalls() {
        final ScriptedChatModel model = new ScriptedChatModel().answerTo(SUMMARY, reply("{}"));

        verdicts.verdict(twoCalls(QualityDial.MAX, place()), FRAME, calls(model));

        assertThat(kinds).containsExactly(CallKind.TERM_READING);
        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(format(request)).isEqualTo(SUMMARY);
            assertThat(user(request)).contains("bowed to the crowd").contains("at dusk, tired and wet");
        });
    }

    @Test
    void verdict_maxAndTwoCallsAgree_sendsNoSummary() {
        final ScriptedChatModel model = new ScriptedChatModel();

        verdicts.verdict(twoCalls(QualityDial.MAX, person()), FRAME, calls(model));

        assertThat(model.requests()).isEmpty();
    }

    // The summary is a Max call: below Max disagreeing calls are decided by the windows' majority alone.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"FAST", "BALANCED"})
    void verdict_belowMaxTwoCallsDisagree_sendsNoSummary(final QualityDial dial) {
        final ScriptedChatModel model = new ScriptedChatModel();

        verdicts.verdict(twoCalls(dial, place()), FRAME, calls(model));

        assertThat(model.requests()).isEmpty();
    }

    // A term read in one call, mentioned fewer than 30 times, has no calls to disagree.
    @Test
    void verdict_maxAndOneCallOfFewMentions_sendsNoSummary() {
        final ScriptedChatModel model = new ScriptedChatModel();
        final Facts oneCall = new Facts(
                untyped(),
                List.of(call(answers(times(3, person()), times(3, place())))),
                List.of(),
                new NameWindows.Evidence(6, List.of(), tally(0, 0)),
                0.0,
                QualityDial.MAX,
                "en");

        verdicts.verdict(oneCall, FRAME, calls(model));

        assertThat(model.requests()).isEmpty();
    }
}
