package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.calls;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.entry;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.says;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
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
 * A brand follows the Book Brief's name policy exactly as a person's name does, and a material, a substance or a drug
 * is a common noun that is translated and never locked (15j.J1). Every name here is invented.
 */
class BrandPolicyTest {

    private static final BookBrief BRIEF = BookBrief.defaults("en");
    private static final CallFrame EN_UK = new CallFrame("en", "uk", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    private static final CallFrame EN_DE = new CallFrame("en", "de", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    private static final BookSlice BOOK = GlossaryTestSegments.of(List.of(
            "Nell bought a Velmora lamp and a coil of Greyfold alloy.",
            "The Velmora shop sold Noxelin tablets and rod KX-7 by the metre."));

    private static final GlossaryEntry VELMORA = entry("Velmora", TermType.TERM, Gender.UNKNOWN, null);
    private static final GlossaryEntry GREYFOLD = entry("Greyfold", TermType.TERM, Gender.UNKNOWN, null);
    private static final GlossaryEntry NOXELIN = entry("Noxelin", TermType.TERM, Gender.UNKNOWN, null);
    private static final GlossaryEntry CODE = entry("KX-7", TermType.TERM, Gender.UNKNOWN, null);

    private final SuggestTargets suggest = new SuggestTargets(new PromptTemplates(), new ObjectMapper());

    private static TargetContext context(final GlossaryEntry entry, final boolean brand) {
        return new TargetContext(
                List.of(entry),
                Map.of(entry.id(), new Reading(entry.term(), List.of(), false, brand)),
                Set.of(entry.id()));
    }

    private Result<List<GlossaryEntry>> written(
            final GlossaryEntry entry,
            final boolean brand,
            final CallFrame frame,
            final NamePolicy policy,
            final ScriptedChatModel model) {
        return suggest.suggestOnto(List.of(entry), BOOK, frame, policy, calls(model), context(entry, brand));
    }

    @Test
    void suggestOnto_brandUnderTransliterate_getsATargetScriptNameAndIsNotLocked() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Velmora", "Вельмора"));

        final Result<List<GlossaryEntry>> result = written(VELMORA, true, EN_UK, NamePolicy.TRANSLITERATE, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Вельмора", false));
    }

    @Test
    void suggestOnto_brandCopiedInLatinUnderTransliterate_isRefusedAndAskedOnceMore() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Velmora", "Velmora")).answer(says("Velmora", "Вельмора"));

        final Result<List<GlossaryEntry>> result = written(VELMORA, true, EN_UK, NamePolicy.TRANSLITERATE, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Вельмора", false));
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void suggestOnto_brandUnderTransliterateWhoseRenderingOpensWithTheWrongLetter_isRefused() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("Velmora", "Белмора")).answer(says("Velmora", "Вельмора"));

        final Result<List<GlossaryEntry>> result = written(VELMORA, true, EN_UK, NamePolicy.TRANSLITERATE, model);

        assertThat(result.data()).extracting(GlossaryEntry::target).containsExactly("Вельмора");
    }

    @Test
    void suggestOnto_brandUnderTranslate_takesTheModelsRenderingUnlocked() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Velmora", "Вельмора"));

        final Result<List<GlossaryEntry>> result = written(VELMORA, true, EN_UK, NamePolicy.TRANSLATE, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Вельмора", false));
    }

    @Test
    void suggestOnto_brandUnderKeepOriginal_keepsItsSourceLockedWithNoCall() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<List<GlossaryEntry>> result = written(VELMORA, true, EN_UK, NamePolicy.KEEP_ORIGINAL, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Velmora", true));
        assertThat(model.requests()).isEmpty();
    }

    // IF a material or a drug were kept in Latin like a brand, THEN the Ukrainian text would hold Latin words for plain
    // nouns (35 of them in the owner's run).
    @Test
    void suggestOnto_materialUnderKeepOriginal_isAskedAndTranslatedUnlocked() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Greyfold", "сіросплав"));

        final Result<List<GlossaryEntry>> result = written(GREYFOLD, false, EN_UK, NamePolicy.KEEP_ORIGINAL, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("сіросплав", false));
    }

    // A noun the reading demoted to OTHER is no name until the readings verify it, so Keep original does not keep it.
    @Test
    void suggestOnto_demotedCommonNounUnderKeepOriginal_isNeitherKeptVerbatimNorLocked() {
        final GlossaryEntry demoted = entry("Greyfold", TermType.OTHER, Gender.UNKNOWN, null);
        final TargetContext unverified = new TargetContext(List.of(demoted), Map.of(), Set.of());
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Greyfold", "сіросплав"));

        final Result<List<GlossaryEntry>> result =
                suggest.suggestOnto(List.of(demoted), BOOK, EN_UK, NamePolicy.KEEP_ORIGINAL, calls(model), unverified);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("сіросплав", false));
    }

    // A phrase of capitalised words is a proper name even when no reading verified it, so Keep original keeps it.
    @Test
    void suggestOnto_unverifiedMultiWordOtherUnderKeepOriginal_keepsItsSourceLockedWithNoCall() {
        final GlossaryEntry guild = entry("Harbour Guild", TermType.OTHER, Gender.UNKNOWN, null);
        final TargetContext unverified = new TargetContext(List.of(guild), Map.of(), Set.of());
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<List<GlossaryEntry>> result =
                suggest.suggestOnto(List.of(guild), BOOK, EN_UK, NamePolicy.KEEP_ORIGINAL, calls(model), unverified);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Harbour Guild", true));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void suggestOnto_drugUnderTransliterate_isTranslatedAsACommonNounNotCheckedAgainstTheLetterTable() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Noxelin", "заспокійливе"));

        final Result<List<GlossaryEntry>> result = written(NOXELIN, false, EN_UK, NamePolicy.TRANSLITERATE, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("заспокійливе", false));
        assertThat(model.requests()).hasSize(1);
    }

    // The German target does not decline names, so a verified proper name is locked there; a common noun never is.
    @Test
    void suggestOnto_materialVerifiedByTheReadingsInAnUninflectingTarget_staysUnlocked() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("Greyfold", "Graulegierung"));

        final Result<List<GlossaryEntry>> result = written(GREYFOLD, false, EN_DE, NamePolicy.TRANSLITERATE, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Graulegierung", false));
    }

    @Test
    void suggestOnto_identifierCode_staysAsWrittenAndLocked() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says("KX-7", "KX-7"));

        final Result<List<GlossaryEntry>> result = written(CODE, false, EN_UK, NamePolicy.TRANSLITERATE, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("KX-7", true));
    }

    // IF an identifier were transliterated, THEN a part number would change between the text and the glossary.
    @Test
    void suggestOnto_identifierGivenATransliteratedRendering_isRefusedAndKeptAsWritten() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(says("KX-7", "КХ-7")).answer(says("KX-7", "KX-7"));

        final Result<List<GlossaryEntry>> result = written(CODE, false, EN_UK, NamePolicy.TRANSLITERATE, model);

        assertThat(result.data())
                .extracting(GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("KX-7", true));
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void suggest_brandUnderTransliterate_isNeverGivenItsSourceWhenTheModelAnswersNothing() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(says());

        final Result<List<Suggestion>> result = suggest.suggest(
                List.of(VELMORA), BOOK, EN_UK, NamePolicy.TRANSLITERATE, calls(model), context(VELMORA, true));

        assertThat(result.data()).isEmpty();
    }
}
