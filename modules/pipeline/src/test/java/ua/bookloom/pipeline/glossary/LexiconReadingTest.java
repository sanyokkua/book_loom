package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.LexiconReplies.reading;
import static ua.bookloom.pipeline.glossary.LexiconReplies.termOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.glossary.LexiconReading.Decision;
import ua.bookloom.pipeline.glossary.LexiconReading.Verdict;
import ua.bookloom.pipeline.index.WindowPlan;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The recurring terms read in context at the dial's plan (15i.N7): Fast puts every term to the one-sentence batch
 * choice; Balanced and Max send one {@code lexicon-reading} call per term with its windows and the code decides from
 * the answers — keep a term the book uses in one sense with its one rendering, drop an ordinary or polysemous word,
 * promote a named thing, leave undecided what cannot be read. The book is invented: thirty days of short paragraphs
 * numbered "Day NN." so the tests can tell which paragraphs a window holds.
 */
class LexiconReadingTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String TERM_CHOICE = "term-choice";
    private static final String LEXICON_READING = "lexicon-reading";
    private static final List<Integer> BEACON_DAYS = List.of(5, 11, 17, 23, 29);
    private static final List<Integer> ANCHOR_DAYS = List.of(8, 20);

    private final List<CallKind> kinds = new ArrayList<>();
    private final List<String> formats = new ArrayList<>();
    private final List<String> users = new ArrayList<>();
    private LexiconReading reading;

    @BeforeEach
    void setUp() {
        final PromptTemplates templates = new PromptTemplates();
        final ObjectMapper mapper = new ObjectMapper();
        reading = new LexiconReading(templates, mapper, new TermChoice(templates, mapper));
    }

    private static String day(final int number) {
        return "Day %02d. ".formatted(number);
    }

    /** Thirty days: the beacon on five of them, the anchor on two, the ferry on all the others. */
    private static BookSlice book() {
        return GlossaryTestSegments.of(IntStream.rangeClosed(1, 30)
                .mapToObj(number -> day(number)
                        + (BEACON_DAYS.contains(number)
                                ? "The beacon was lit before the wind rose."
                                : ANCHOR_DAYS.contains(number)
                                        ? "The anchor dragged in the mud."
                                        : "The ferry crossed the bay slowly."))
                .toList());
    }

    private static String formatOf(final ChatRequest request) {
        return request.responseFormat() == null
                ? ""
                : Objects.requireNonNull(request.responseFormat()).name();
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    /** A model that answers a reading by the term the prompt marks and a batch choice with {@code choice}. */
    private ModelCalls model(final Function<String, String> readingFor, final String choice) {
        return (kind, segmentId, request) -> {
            final String user = request.messages().getLast().content();
            kinds.add(kind);
            formats.add(formatOf(request));
            users.add(user);
            return reply(formatOf(request).equals(TERM_CHOICE) ? choice : readingFor.apply(termOf(user)));
        };
    }

    private ModelCalls model(final Function<String, String> readingFor) {
        return model(readingFor, "{\"terms\":[]}");
    }

    private static Map<String, Decision> decided(final Result<Map<String, Decision>> result) {
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private static Decision decisionOf(final Map<String, Decision> decisions, final String key) {
        assertThat(decisions).as("a decision for %s", key).containsKey(key);
        return Objects.requireNonNull(decisions.get(key), key);
    }

    private Map<String, Decision> readBalanced(final List<String> terms, final Function<String, String> readingFor) {
        return decided(reading.read(terms, book(), QualityDial.BALANCED, FRAME, model(readingFor)));
    }

    private List<String> usersAbout(final String term) {
        return users.stream().filter(user -> user.contains("[[" + term + "]]")).toList();
    }

    // The windows per dial: Fast one sentence, Balanced three windows one neighbour each side, Max four of two.
    @ParameterizedTest
    @CsvSource({"FAST,1,0", "BALANCED,3,1", "MAX,4,2"})
    void planOf_dial_isTheWindowsAndNeighboursTheTaskNames(
            final QualityDial dial, final int windows, final int neighbours) {
        final WindowPlan plan = LexiconReading.planOf(dial);

        assertThat(plan.count()).isEqualTo(windows);
        assertThat(plan.neighbours()).isEqualTo(neighbours);
    }

    // IF Fast read every term in windows, THEN the quick dial would cost a call per term: it asks the one batch.
    @Test
    void read_fast_putsAllTermsToOneBatchChoiceAndSendsNoReading() {
        final String choice = "{\"terms\":[{\"term\":\"beacon\",\"keep\":true},{\"term\":\"ferry\",\"keep\":false},"
                + "{\"term\":\"anchor\",\"keep\":true}]}";

        final Map<String, Decision> decisions = decided(reading.read(
                List.of("beacon", "ferry", "anchor"), book(), QualityDial.FAST, FRAME, model(term -> "{}", choice)));

        assertThat(kinds).containsExactly(CallKind.TERM_CHOICE);
        assertThat(formats).containsExactly(TERM_CHOICE);
        assertThat(decisions)
                .containsEntry("beacon", new Decision(Verdict.KEEP, null))
                .containsEntry("ferry", new Decision(Verdict.DROP, null))
                .containsEntry("anchor", new Decision(Verdict.KEEP, null));
    }

    // A term the batch answer leaves out is neither kept nor dropped.
    @Test
    void read_fastTermTheAnswerLeavesOut_isUndecided() {
        final String choice = "{\"terms\":[{\"term\":\"beacon\",\"keep\":true}]}";

        final Map<String, Decision> decisions = decided(
                reading.read(List.of("beacon", "ferry"), book(), QualityDial.FAST, FRAME, model(term -> "{}", choice)));

        assertThat(decisionOf(decisions, "ferry")).isEqualTo(new Decision(Verdict.UNDECIDED, null));
    }

    // One call per term at Balanced: a small model mixes the windows of several terms.
    @Test
    void read_balanced_sendsOneReadingCallPerTermWithThreeWindowsAtMost() {
        readBalanced(List.of("beacon", "ferry", "anchor"), term -> reading(term, "term", 1, "x"));

        assertThat(kinds).containsExactly(CallKind.TERM_READING, CallKind.TERM_READING, CallKind.TERM_READING);
        assertThat(formats).containsOnly(LEXICON_READING);
        assertThat(usersAbout("beacon")).singleElement().asString().contains("[1]", "[2]", "[3]");
        assertThat(usersAbout("beacon")).singleElement().asString().doesNotContain("[4]");
        assertThat(usersAbout("ferry"))
                .singleElement()
                .asString()
                .contains("[3]")
                .doesNotContain("[4]");
    }

    // A term the story writes twice has two windows to show, not three.
    @Test
    void read_balancedTermWrittenTwice_showsTwoWindows() {
        readBalanced(List.of("anchor"), term -> reading(term, "term", 1, "x"));

        assertThat(usersAbout("anchor"))
                .singleElement()
                .asString()
                .contains("[2]")
                .doesNotContain("[3]");
    }

    // IF a window held more than the paragraph before and after, THEN the model would read a scene it was not asked
    // about; the days two away from a mention (3, 7, 9, 13, 15, 19, 21, 25, 27) never appear, the days beside it do.
    @Test
    void read_balanced_windowsHoldOneNeighbourEachSideAndNoMore() {
        readBalanced(List.of("beacon"), term -> reading(term, "term", 1, "x"));

        assertThat(usersAbout("beacon"))
                .singleElement()
                .asString()
                .containsAnyOf(day(4), day(6), day(10), day(12), day(16), day(18), day(22), day(24), day(28), day(30))
                .doesNotContain(day(3), day(7), day(9), day(13), day(15), day(19), day(21), day(25), day(27));
    }

    @Test
    void read_max_showsFourWindowsOfTwoNeighbours() {
        decided(reading.read(
                List.of("beacon"), book(), QualityDial.MAX, FRAME, model(term -> reading(term, "term", 1, "x"))));

        assertThat(usersAbout("beacon"))
                .singleElement()
                .asString()
                .contains("[4]")
                .doesNotContain("[5]")
                .containsAnyOf(day(3), day(7), day(9), day(13), day(15), day(19), day(21), day(25), day(27));
    }

    // The windows mark the term where the window was cut, as the name reading does.
    @Test
    void read_balanced_userMarksTheTermInEachWindow() {
        readBalanced(List.of("beacon"), term -> reading(term, "term", 1, "x"));

        assertThat(usersAbout("beacon"))
                .singleElement()
                .asString()
                .contains("[1] ")
                .contains("[[beacon]]")
                .doesNotContain("{{", "}}");
    }

    // IF the term was written with a capital at the start of a sentence, THEN those windows would be lost.
    @Test
    void read_termInOtherCase_isFoundWhateverItsCaseAndKeyedByTheLexiconKey() {
        final BookSlice cased = GlossaryTestSegments.of(IntStream.rangeClosed(1, 6)
                .mapToObj(
                        number -> day(number) + (number % 2 == 0 ? "Beacon fires lit the hill." : "The beacon burned."))
                .toList());

        final Map<String, Decision> decisions = decided(reading.read(
                List.of("Beacon"),
                cased,
                QualityDial.BALANCED,
                FRAME,
                model(term -> reading(term, "term", 1, "маяк"))));

        assertThat(users).singleElement().asString().contains("[1]", "[2]", "[3]");
        assertThat(decisions).containsOnlyKeys(LexiconEntry.keyOf("Beacon"));
    }

    // keep: the book uses the term in one sense in every window.
    @Test
    void read_oneSenseInEveryWindow_keepsTheTermWithItsRendering() {
        final Map<String, Decision> decisions =
                readBalanced(List.of("beacon"), term -> reading(term, "term", 1, "маяк"));

        assertThat(decisionOf(decisions, "beacon")).isEqualTo(new Decision(Verdict.KEEP, "маяк"));
    }

    @Test
    void read_oneSenseWithNoRendering_keepsTheTermWithNone() {
        final Map<String, Decision> decisions = readBalanced(List.of("beacon"), term -> reading(term, "term", 1, ""));

        assertThat(decisionOf(decisions, "beacon")).isEqualTo(new Decision(Verdict.KEEP, null));
    }

    // drop: an ordinary word is no term.
    @Test
    void read_ordinaryWordInEveryWindow_dropsIt() {
        final Map<String, Decision> decisions =
                readBalanced(List.of("ferry"), term -> reading(term, "word", 1, "пором"));

        assertThat(decisionOf(decisions, "ferry").verdict()).isEqualTo(Verdict.DROP);
    }

    // drop: a word the book uses in two senses cannot have one rendering, whatever the model offers.
    @Test
    void read_twoSensesAcrossTheWindows_dropsThePolysemousWord() {
        final Map<String, Decision> decisions =
                readBalanced(List.of("beacon"), term -> reading(term, "term", 2, "маяк"));

        assertThat(decisionOf(decisions, "beacon").verdict()).isEqualTo(Verdict.DROP);
    }

    // promote: a named thing belongs in the glossary, with the rendering that fits every window.
    @Test
    void read_namedThingInEveryWindow_isPromotedWithItsRendering() {
        final Map<String, Decision> decisions =
                readBalanced(List.of("beacon"), term -> reading(term, "name", 1, "Маяк"));

        assertThat(decisionOf(decisions, "beacon")).isEqualTo(new Decision(Verdict.PROMOTE, "Маяк"));
    }

    // The three windows shown decide; a window number the call never showed is ignored (here 4 to 6 say "word").
    @Test
    void read_answersForWindowsNotShown_areIgnored() {
        final String mixed = "{\"windows\":[{\"window\":1,\"quote\":\"beacon\",\"usage\":\"term\"},"
                + "{\"window\":2,\"quote\":\"beacon\",\"usage\":\"term\"},"
                + "{\"window\":3,\"quote\":\"beacon\",\"usage\":\"term\"},"
                + "{\"window\":4,\"quote\":\"beacon\",\"usage\":\"word\"},"
                + "{\"window\":5,\"quote\":\"beacon\",\"usage\":\"word\"},"
                + "{\"window\":6,\"quote\":\"beacon\",\"usage\":\"word\"}],\"senses\":1,\"rendering\":\"маяк\"}";

        final Map<String, Decision> decisions = readBalanced(List.of("beacon"), term -> mixed);

        assertThat(decisionOf(decisions, "beacon")).isEqualTo(new Decision(Verdict.KEEP, "маяк"));
    }

    // IF a quote that stands in no window counted, THEN the model could answer without reading: the labels of a window
    // whose quote the code cannot find are ignored, and a term with no window left is undecided.
    @Test
    void read_quoteThatStandsInNoWindow_countsNoWindow() {
        final String invented = "{\"windows\":[{\"window\":1,\"quote\":\"the lighthouse keeper\",\"usage\":\"term\"},"
                + "{\"window\":2,\"quote\":\"the lighthouse keeper\",\"usage\":\"term\"},"
                + "{\"window\":3,\"quote\":\"the lighthouse keeper\",\"usage\":\"term\"}],"
                + "\"senses\":1,\"rendering\":\"маяк\"}";

        final Map<String, Decision> decisions = readBalanced(List.of("beacon"), term -> invented);

        assertThat(decisionOf(decisions, "beacon")).isEqualTo(new Decision(Verdict.UNDECIDED, null));
    }

    // An unreadable answer decides nothing for that term and is no error: one bad reply must not lose the others.
    @Test
    void read_unreadableReply_isUndecidedAndNoError() {
        final Map<String, Decision> decisions = readBalanced(
                List.of("beacon", "ferry"),
                term -> term.equals("beacon") ? "I would keep it." : reading(term, "word", 1, ""));

        assertThat(decisionOf(decisions, "beacon")).isEqualTo(new Decision(Verdict.UNDECIDED, null));
        assertThat(decisionOf(decisions, "ferry").verdict()).isEqualTo(Verdict.DROP);
    }

    // A term the story never writes has no window to show: no call, undecided.
    @Test
    void read_termTheStoryNeverWrites_isUndecidedWithNoCall() {
        final Map<String, Decision> decisions =
                readBalanced(List.of("lantern"), term -> reading(term, "term", 1, "ліхтар"));

        assertThat(decisionOf(decisions, "lantern")).isEqualTo(new Decision(Verdict.UNDECIDED, null));
        assertThat(kinds).isEmpty();
    }

    // IF a failed call were read as an empty answer, THEN a provider outage would drop nothing and say nothing.
    @Test
    void read_providerFailure_returnsTheErrorAndNoDecisions() {
        final ModelCalls failing =
                (kind, segmentId, request) -> Result.err(AppError.of(ErrorCode.unreachable, "Offline", "no provider"));

        final Result<Map<String, Decision>> result =
                reading.read(List.of("beacon"), book(), QualityDial.BALANCED, FRAME, failing);

        assertThat(result.isErr()).isTrue();
        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
        assertThat(result.data()).isNull();
    }

    // The one identity rule (15i.N6): "beacon" kept as "beacon" in a Ukrainian book is no translation, so the term is
    // kept with no rendering and the suggestion step gives it one.
    @Test
    void read_renderingThatIsTheSourceWordAcrossScripts_isRefusedAndTheTermKeptWithNone() {
        final Map<String, Decision> decisions =
                readBalanced(List.of("beacon"), term -> reading(term, "term", 1, "beacon"));

        assertThat(decisionOf(decisions, "beacon")).isEqualTo(new Decision(Verdict.KEEP, null));
    }

    // An identifier is the one thing that may stay as written across scripts.
    @Test
    void read_identifierRenderedAsItself_isKept() {
        final BookSlice machines = GlossaryTestSegments.of(IntStream.rangeClosed(1, 6)
                .mapToObj(number -> day(number) + "The ZX-81 hummed in the corner.")
                .toList());

        final Map<String, Decision> decisions = decided(reading.read(
                List.of("ZX-81"),
                machines,
                QualityDial.BALANCED,
                FRAME,
                model(term -> reading(term, "term", 1, "ZX-81"))));

        assertThat(decisionOf(decisions, LexiconEntry.keyOf("ZX-81"))).isEqualTo(new Decision(Verdict.KEEP, "ZX-81"));
    }
}
