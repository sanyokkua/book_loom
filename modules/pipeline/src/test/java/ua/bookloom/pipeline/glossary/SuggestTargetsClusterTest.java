package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.calls;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.entry;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.says;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
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
 * The renderings of one person's spellings agree word by word (15i.N6): the full name's rendering is the cluster's
 * chosen one, whether the full name is asked in the same batch or held with a rendering, and a held spelling's
 * rendering is kept when the full name is the one asked.
 */
class SuggestTargetsClusterTest {

    private static final CallFrame EN_UK =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final BookSlice BOOK =
            GlossaryTestSegments.of(List.of("Arlo Venn opened the door.", "Venn shut it again."));
    private static final GlossaryEntry ARLO = entry("Arlo", TermType.CHARACTER, Gender.MALE, null);
    private static final GlossaryEntry VENN = entry("Venn", TermType.CHARACTER, Gender.MALE, null);
    private static final GlossaryEntry ARLO_VENN = entry("Arlo Venn", TermType.CHARACTER, Gender.MALE, null);
    private static final Map<String, GlossaryEntry> MEMBERS = Map.of("Venn", VENN, "Arlo", ARLO);

    private final SuggestTargets suggest = new SuggestTargets(new PromptTemplates(), new ObjectMapper());

    // IF a one-word spelling of a person is rendered differently from the same word in the full name, THEN it takes
    // the full name's word: the full name is the cluster's chosen rendering.
    @ParameterizedTest(name = "{0} rendered {1} becomes {2}")
    @CsvSource({"Venn,Вен,Венн", "Arlo,Арлі,Арло", "Venn,Венн,Венн"})
    void suggest_memberAskedWithItsFullNameInTheBatch_agreesWithTheFullNamesWord(
            final String term, final String reply, final String expected) {
        final GlossaryEntry member = MEMBERS.get(term);
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Arlo Venn", "Арло Венн", term, reply));

        final Result<List<Suggestion>> suggested =
                suggest.suggest(List.of(ARLO_VENN, member), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data())
                .extracting(s -> s.entry().term(), Suggestion::target)
                .containsExactlyInAnyOrder(tuple("Arlo Venn", "Арло Венн"), tuple(term, expected));
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void suggest_memberAskedAgainstAHeldFullName_agreesWithTheFullNamesWord() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Venn", "Вен"));

        final Result<List<Suggestion>> suggested = suggest.suggest(
                List.of(VENN),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(ARLO_VENN.withTarget("Арло Венн"), VENN), Map.of()));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Венн");
    }

    @Test
    void suggest_fullNameAskedAgainstAHeldMember_agreesWithTheMembersWord() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Arlo Venn", "Арло Вен"));

        final Result<List<Suggestion>> suggested = suggest.suggest(
                List.of(ARLO_VENN),
                BOOK,
                EN_UK,
                NamePolicy.TRANSLITERATE,
                calls(model),
                new TargetContext(List.of(ARLO_VENN, VENN.withTarget("Венн")), Map.of()));

        assertThat(suggested.data()).extracting(Suggestion::target).containsExactly("Арло Венн");
    }

    // A surname two people share joins neither person's cluster, but one word of a person's name has one rendering in
    // every name (15i.N gate): the family's surname is spelled alike, and nobody's first name is touched.
    @Test
    void suggest_surnameOfTwoPeople_joinsNeitherButTakesTheirSharedSpelling() {
        final GlossaryEntry otherVenn = entry("Mira Venn", TermType.CHARACTER, Gender.FEMALE, null);
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Arlo Venn", "Арло Венн", "Mira Venn", "Міра Венн", "Venn", "Вен"));

        final Result<List<Suggestion>> suggested = suggest.suggest(
                List.of(ARLO_VENN, otherVenn, VENN), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model));

        assertThat(suggested.data())
                .extracting(s -> s.entry().term(), Suggestion::target)
                .containsExactly(
                        tuple("Arlo Venn", "Арло Венн"), tuple("Mira Venn", "Міра Венн"), tuple("Venn", "Венн"));
    }
}
