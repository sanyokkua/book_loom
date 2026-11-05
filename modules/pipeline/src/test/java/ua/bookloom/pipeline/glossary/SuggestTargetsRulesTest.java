package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.calls;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.entry;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.says;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.AnalysisData;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** Renderings the pair's data fixes and common words inside names: what is decided without or against the model. */
class SuggestTargetsRulesTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final BookSlice BOOK = GlossaryTestSegments.of(List.of(
            "The houses stood on the hill.",
            "Many houses were dark that night.",
            "She sold the houses in spring.",
            "Houses of Parliament were old.",
            "Mr Hale came."));

    private final SuggestTargets suggest = new SuggestTargets(new PromptTemplates(), new ObjectMapper());

    private static List<String> termLines(final ScriptedChatModel model) {
        return AnalysisData.of(model.requests().getFirst().messages().get(1).content())
                .lines()
                .filter(line -> line.startsWith("- "))
                .toList();
    }

    // IF the model were asked, a small one answers Latin "Pan" or a transliterated "місіс" for a form of address.
    @Test
    void suggest_titleOfThePairsMap_isRenderedByDataWithoutAskingTheModel() {
        final ScriptedChatModel model = new ScriptedChatModel();
        final List<GlossaryEntry> titles = List.of(
                entry("Mr", TermType.TITLE, Gender.UNKNOWN, null), entry("ms", TermType.TERM, Gender.UNKNOWN, null));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(titles, BOOK, FRAME, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data())
                .extracting(suggestion -> suggestion.entry().term(), Suggestion::target)
                .containsExactly(tuple("Mr", "Пан"), tuple("ms", "пані"));
        assertThat(model.requests()).isEmpty();
    }

    // The window's Auto source on a book with no declared language has no analysis language; the titles are still
    // rendered by the data, and the model is not asked about them.
    @Test
    void suggest_titlesUnderAnAutoSource_areRenderedByDataWithoutAskingTheModel() {
        final CallFrame auto =
                new CallFrame(null, "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        final ScriptedChatModel model = new ScriptedChatModel();
        final List<GlossaryEntry> titles = List.of(
                entry("Ms", TermType.TERM, Gender.UNKNOWN, null),
                entry("Mrs.", TermType.TERM, Gender.UNKNOWN, null),
                entry("Mr.", TermType.TERM, Gender.UNKNOWN, null));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(titles, BOOK, auto, NamePolicy.TRANSLATE, calls(model));

        assertThat(suggested.data())
                .extracting(suggestion -> suggestion.entry().term(), Suggestion::target)
                .containsExactly(tuple("Ms", "Пані"), tuple("Mrs.", "Пані"), tuple("Mr.", "Пан"));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void suggestOnto_titleOfThePairsMap_isLockedWithRuleOriginSoNoModelOverridesIt() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<List<GlossaryEntry>> suggested = suggest.suggestOnto(
                List.of(entry("mrs", TermType.TITLE, Gender.UNKNOWN, null)),
                BOOK,
                FRAME,
                NamePolicy.TRANSLATE,
                calls(model));

        assertThat(suggested.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::targetState, GlossaryEntry::targetOrigin)
                .containsExactly(tuple("пані", TargetState.LOCKED, Origin.RULE));
    }

    // A Latin answer is no rendering in Ukrainian; the title the data does not list is refused, asked once more and
    // left without a target rather than stored in the wrong script.
    @Test
    void suggest_unlistedTitleAnsweredInLatin_isLeftWithoutATarget() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("constable", "Konstebl")).answer(says("constable", "Konstebl"));

        final Result<List<Suggestion>> suggested = suggest.suggest(
                List.of(entry("constable", TermType.TITLE, Gender.UNKNOWN, null)),
                BOOK,
                FRAME,
                NamePolicy.TRANSLATE,
                calls(model));

        assertThat(suggested.data()).isEmpty();
    }

    // IF a place named by a common word were sounded out, THEN "Houses" would be "Хаузиз" and the book would read it
    // beside "палати" for the same word.
    @Test
    void suggest_placeThatIsACommonWord_isAskedAsACommonWordAndMayBeTranslated() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Houses", "Будинки"));

        final Result<List<Suggestion>> suggested = suggest.suggest(
                List.of(entry("Houses", TermType.PLACE, Gender.UNKNOWN, null)),
                BOOK,
                FRAME,
                NamePolicy.TRANSLITERATE,
                calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Будинки");
        assertThat(termLines(model).getFirst()).startsWith("- Houses — common word, translate it");
    }

    @Test
    void suggest_nameHoldingACommonWord_isToldToTranslateThatWordAndNeedNotBeSoundedOut() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Houses of Parliament", "Палати парламенту"));

        final Result<List<Suggestion>> suggested = suggest.suggest(
                List.of(entry("Houses of Parliament", TermType.PLACE, Gender.UNKNOWN, null)),
                BOOK,
                FRAME,
                NamePolicy.TRANSLITERATE,
                calls(model));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Палати парламенту");
        assertThat(termLines(model).getFirst()).contains("translate the common word: Houses");
    }

    @Test
    void suggest_placeNeverWrittenInLowerCase_staysAName() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Milton", "Мілтон"));

        suggest.suggest(
                List.of(entry("Milton", TermType.PLACE, Gender.UNKNOWN, null)),
                GlossaryTestSegments.of(List.of("Hale walked to Milton.", "Milton lay ahead.")),
                FRAME,
                NamePolicy.TRANSLITERATE,
                calls(model));

        assertThat(termLines(model).getFirst()).startsWith("- Milton — place");
    }
}
