package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.calls;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.entry;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.says;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * A suggested target is refused, and asked once more, when it loses a word of a multi-word name, opens with a letter the
 * language file's {@code translit} does not allow, holds a letter the target language does not use, or ends in a case
 * form of a name that ends in a consonant (15j.J4, 15j.J10); asked a second time and refused again it leaves the entry
 * without a target. Every name here is invented.
 */
class SuggestionRefusalsTest {

    private static final BookBrief BRIEF = BookBrief.defaults("en");
    private static final CallFrame EN_UK = new CallFrame("en", "uk", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    private static final BookSlice BOOK = GlossaryTestSegments.of(List.of(
            "Nell reached Varno City at dusk.",
            "Charrow lay beyond the hills, and Valdor was far.",
            "Brynn and Sarah waited."));

    private final SuggestTargets suggest = new SuggestTargets(new PromptTemplates(), new ObjectMapper());

    private Result<List<Suggestion>> asked(
            final GlossaryEntry entry, final NamePolicy policy, final ScriptedChatModel model) {
        return suggest.suggest(List.of(entry), BOOK, EN_UK, policy, calls(model));
    }

    private static GlossaryEntry place(final String term) {
        return entry(term, TermType.PLACE, Gender.UNKNOWN, null);
    }

    private static String lastUserText(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .reduce("", (a, b) -> a + b);
    }

    @Test
    void suggest_placeThatLosesItsSecondWord_isRefusedAndAskedOnceMoreNamingTheLoss() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Varno City", "Варно")).answer(says("Varno City", "Варно Сіті"));

        final Result<List<Suggestion>> result = asked(place("Varno City"), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly("Варно Сіті");
        assertThat(model.requests()).hasSize(2);
        assertThat(lastUserText(model.requests().get(1))).contains("refused", "Варно", "leaves out a word");
    }

    @Test
    void suggest_placeWhoseSecondWordIsTranslatedToACommonWord_isKept() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Varno City", "місто Варно"));

        final Result<List<Suggestion>> result = asked(place("Varno City"), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly("місто Варно");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void suggest_placeLosingAWordTwice_isLeftWithoutATarget() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Varno City", "Варно")).answer(says("Varno City", "Варно"));

        final Result<List<Suggestion>> result = asked(place("Varno City"), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).isEmpty();
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void suggest_placeLosingAWordUnderTranslate_isRefusedToo() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Varno City", "Варно")).answer(says("Varno City", "Варно Сіті"));

        final Result<List<Suggestion>> result = asked(place("Varno City"), NamePolicy.TRANSLATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly("Варно Сіті");
    }

    @Test
    void suggest_renderingThatOpensWithTheWrongLetter_isRefusedAndTheRightOneKept() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Charrow", "Сарроу")).answer(says("Charrow", "Чарроу"));

        final Result<List<Suggestion>> result = asked(place("Charrow"), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly("Чарроу");
        assertThat(lastUserText(model.requests().get(1))).contains("does not begin the way the name sounds");
    }

    @Test
    void suggest_renderingWithALetterTheTargetLanguageDoesNotUse_isRefusedAndAskedOnceMore() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Brynn", "Брынн")).answer(says("Brynn", "Бринн"));

        final Result<List<Suggestion>> result =
                asked(entry("Brynn", TermType.CHARACTER, Gender.MALE, null), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly("Бринн");
        assertThat(lastUserText(model.requests().get(1))).contains("letter the target language does not use");
    }

    @Test
    void suggest_commonNounWithARussianLetter_isRefusedUnderEveryPolicy() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Sons", "Сыны")).answer(says("Sons", "Сини"));

        final Result<List<Suggestion>> result =
                asked(entry("Sons", TermType.TERM, Gender.UNKNOWN, null), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly("Сини");
    }

    @ParameterizedTest(name = "{0} -> {1} refused, then {2}")
    @CsvSource({"Valdor,Валдором,Валдор", "Brann,Бранном,Бранн", "Venn,Венном,Венн", "Valdor,Валдорами,Валдор"})
    void suggest_nameThatEndsInAConsonantAndIsGivenInACaseForm_isRefusedForTheDictionaryForm(
            final String term, final String declined, final String dictionary) {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says(term, declined)).answer(says(term, dictionary));

        final Result<List<Suggestion>> result = asked(place(term), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly(dictionary);
        assertThat(lastUserText(model.requests().get(1))).contains("case form");
    }

    // A rendering is a case form only when it is the source's own transliteration plus an oblique ending: a name that
    // merely ends like one (Том, Львів, Багами) or is no transliteration of the source (Темзор) is kept.
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "Tom,Том",
        "Adam,Адам",
        "Sam,Сам",
        "Jem,Джем",
        "Tarvi,Тарві",
        "Ann,Анна",
        "Jess,Джессі",
        "Bess,Бессі",
        "Pavlo,Павло",
        "Elin,Елін",
        "Baku,Баку",
        "Tbilisi,Тбілісі",
        "Prague,Прага",
        "Kyiv,Київ",
        "Lviv,Львів",
        "Kharkiv,Харків",
        "Petriv,Петрів",
        "Bahamas,Багами",
        "Thamesor,Темзор"
    })
    void suggest_nameThatOnlyEndsLikeACaseForm_isKept(final String term, final String rendering) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says(term, rendering));

        final Result<List<Suggestion>> result = asked(place(term), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly(rendering);
        assertThat(model.requests()).hasSize(1);
    }

    // The source's own last letter decides: a name that ends in a vowel, or in w, y or h, may end in -а or -у.
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"Sarah,Сара", "Arlo,Арло", "Charrow,Чарроу", "Dana,Дана"})
    void suggest_nameWhoseSourceDoesNotEndInAConsonant_isKeptWithTheEndingItHas(
            final String term, final String rendering) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says(term, rendering));

        final Result<List<Suggestion>> result = asked(place(term), NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(Suggestion::target).containsExactly(rendering);
        assertThat(model.requests()).hasSize(1);
    }
}
