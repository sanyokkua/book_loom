package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.calls;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.entry;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.reply;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.says;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * Names translated with context (15i.N6): what each name's line carries, the letter-table and identity refusals with
 * their one re-ask, the exception for an identifier, and the agreement of a cluster's renderings. The
 * model is scripted at the call seam; the prompt text and the requests are asserted as the model would see them.
 */
class SuggestTargetsContextTest {

    private static final BookBrief BRIEF = BookBrief.defaults("en");
    private static final CallFrame EN_UK = new CallFrame("en", "uk", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    private static final CallFrame EN_DE = new CallFrame("en", "de", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    private static final BookSlice BOOK = GlossaryTestSegments.of(List.of(
            "Arlo Venn opened the door.",
            "Venn shut it again.",
            "The Rider rode on while Kessel Optik sold lenses and the ZX-81 hummed."));
    private static final String NOTHING = "{\"suggestions\":[]}";

    private static final GlossaryEntry ARLO = entry("Arlo", TermType.CHARACTER, Gender.MALE, null);
    private static final GlossaryEntry VENN = entry("Venn", TermType.CHARACTER, Gender.MALE, null);
    private static final GlossaryEntry ARLO_VENN = entry("Arlo Venn", TermType.CHARACTER, Gender.MALE, null);
    private static final GlossaryEntry RIDER = entry("Rider", TermType.CHARACTER, Gender.MALE, null);
    private static final GlossaryEntry KESSEL = entry("KESSEL OPTIK", TermType.OTHER, Gender.UNKNOWN, null);
    private static final GlossaryEntry ZX = entry("ZX-81", TermType.OTHER, Gender.UNKNOWN, null);
    private static final GlossaryEntry AMULET = entry("Amulet", TermType.TERM, Gender.UNKNOWN, null);

    private static final Map<String, GlossaryEntry> MEMBERS = Map.of("Venn", VENN, "Arlo", ARLO);

    private final SuggestTargets suggest = new SuggestTargets(new PromptTemplates(), new ObjectMapper());

    // ---- what the call carries -------------------------------------------------------------------------------

    // IF a full name already has a rendering, THEN the line of each other spelling of the person states it and the
    // word the spelling takes from it, so the model has only to repeat it.
    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({"Venn,'Arlo Venn → Арло Венн, so Venn → Венн'", "Arlo,'Arlo Venn → Арло Венн, so Arlo → Арло'"})
    void suggest_memberOfAClusterWhoseFullNameHasARendering_lineStatesTheRenderingAndTheMembersWord(
            final String term, final String expected) {
        final GlossaryEntry anchor = ARLO_VENN.withTarget("Арло Венн");
        final GlossaryEntry asked = MEMBERS.get(term);
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(NOTHING));

        suggest.suggest(
                List.of(asked),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(anchor, VENN, ARLO), Map.of()));

        assertThat(lineOf(model.requests().getFirst(), term)).contains(expected);
    }

    @Test
    void suggest_nameWithNoCluster_lineHasNoClusterStatement() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(NOTHING));

        suggest.suggest(
                List.of(RIDER),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(RIDER, ARLO_VENN.withTarget("Арло Венн")), Map.of()));

        assertThat(lineOf(model.requests().getFirst(), "Rider")).doesNotContain("→", "so ");
    }

    @Test
    void suggest_nameThatTheBookMentionsTwice_lineCarriesTypeGenderAndBothExamples() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(NOTHING));

        suggest.suggest(
                List.of(VENN),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(VENN), Map.of()));

        assertThat(lineOf(model.requests().getFirst(), "Venn"))
                .contains("person, male", "Arlo Venn opened the door.", "Venn shut it again.");
    }

    // IF the reading says a name is speaking or a brand, THEN only that name's line says so.
    @Test
    void suggest_readingFlags_markSpeakingAndBrandOnTheirOwnLinesOnly() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(NOTHING));
        final Map<String, Reading> readings = Map.of(
                RIDER.id(), new Reading("Rider", List.of(), true, false),
                KESSEL.id(), new Reading("KESSEL OPTIK", List.of(), false, true),
                VENN.id(), Reading.none("Venn"));

        suggest.suggest(
                List.of(RIDER, KESSEL, VENN),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(RIDER, KESSEL, VENN), readings));

        final ChatRequest request = model.requests().getFirst();
        assertThat(lineOf(request, "Rider")).contains("speaking").doesNotContain("brand");
        assertThat(lineOf(request, "KESSEL OPTIK")).contains("brand").doesNotContain("speaking");
        assertThat(lineOf(request, "Venn")).doesNotContain("speaking", "brand");
    }

    @Test
    void suggest_namePolicy_isStatedInTheSystemMessageOfTheContextCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(NOTHING));

        suggest.suggest(
                List.of(VENN),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(VENN), Map.of()));

        assertThat(model.requests().getFirst().messages().getFirst().content())
                .contains("practical transcription of foreign names");
    }

    // ---- the letter table and the one re-ask -----------------------------------------------------------------

    // IF a rendering does not start as its name transliterates, THEN that name alone is asked once more, told what
    // was refused.
    @Test
    void suggest_renderingFailsTheLetterTable_asksThatNameOnceMore() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(says("Arlo", "Карло", "Venn", "Венн"))
                .answer(says("Arlo", "Арло"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(ARLO, VENN), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data())
                .extracting(suggestion -> suggestion.entry().term(), Suggestion::target)
                .containsExactlyInAnyOrder(tuple("Arlo", "Арло"), tuple("Venn", "Венн"));
        assertThat(model.requests()).hasSize(2);
        assertThat(termLines(model.requests().get(1)))
                .hasSize(1)
                .first()
                .asString()
                .startsWith("- Arlo");
        assertThat(userText(model.requests().get(1))).contains("Карло");
        assertThat(model.requests().get(1).callKind()).isEqualTo(CallKind.SUGGEST_TARGETS);
    }

    @Test
    void suggest_renderingStillFailsTheLetterTable_leavesTheNameWithoutATarget() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(says("Arlo", "Карло", "Venn", "Венн"))
                .answer(says("Arlo", "Карло"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(ARLO, VENN), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data()).extracting(s -> s.entry().term()).containsExactly("Venn");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void suggest_everyRenderingHolds_makesOneCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Arlo", "Арло", "Venn", "Венн"));

        suggest.suggest(List.of(ARLO, VENN), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void suggest_askedOnceMoreAndTheCallFails_returnsThatError() {
        final AppError failure = AppError.of(ErrorCode.timeout, "Too slow", "The provider did not answer in time.");
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Arlo", "Карло")).answer(Result.err(failure));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(ARLO), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.error()).isEqualTo(failure);
    }

    // The letter table is for names that are transliterated: a translated name, a term and a speaking name are not
    // held to it.
    @Test
    void suggest_translatePolicy_doesNotHoldANameToTheLetterTable() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Rider", "Вершник"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(RIDER), BOOK, EN_UK, NamePolicy.TRANSLATE, calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Вершник");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void suggest_termUnderTransliterate_isNotHeldToTheLetterTable() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Amulet", "Талісман"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(AMULET), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Талісман");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void suggest_speakingNameUnderTransliterate_isNotHeldToTheLetterTable() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Rider", "Вершник"));

        final Result<List<Suggestion>> suggested = suggest.suggest(
                List.of(RIDER),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(RIDER), Map.of(RIDER.id(), new Reading("Rider", List.of(), true, false))));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Вершник");
        assertThat(model.requests()).hasSize(1);
    }

    // ---- the identity rule -----------------------------------------------------------------------------------

    @Test
    void suggest_nameCopiedAcrossScripts_isRefusedAndAskedOnceMore() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Arlo", "Arlo")).answer(says("Arlo", "Арло"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(ARLO), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Арло");
        assertThat(model.requests()).hasSize(2);
        assertThat(termLines(model.requests().get(1))).hasSize(1);
    }

    @Test
    void suggest_sameCopyOfAnUnflaggedName_isRefusedAndAskedOnceMore() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(says("KESSEL OPTIK", "KESSEL OPTIK"))
                .answer(says("KESSEL OPTIK", "Кессел Оптік"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(KESSEL), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Кессел Оптік");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void suggest_identifierCopiedVerbatim_isKeptWithoutAnotherCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("ZX-81", "ZX-81"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(ZX), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("ZX-81");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void suggest_copyInTheSameScript_isAllowed() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Arlo", "Arlo"));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(ARLO), BOOK, EN_DE, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Arlo");
        assertThat(model.requests()).hasSize(1);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static String userText(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .collect(Collectors.joining("\n"));
    }

    private static List<String> termLines(final ChatRequest request) {
        return userText(request).lines().filter(line -> line.startsWith("- ")).toList();
    }

    /** The line of the given term; the term is followed by a space, so "Venn" never matches "Venn Street". */
    private static String lineOf(final ChatRequest request, final String term) {
        return termLines(request).stream()
                .filter(line -> line.startsWith("- " + term + " "))
                .findFirst()
                .orElse("");
    }
}
