package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpan;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.LockedRendering;

class WindowMapTest {

    private static final String PROJECT = "p1";

    private static GlossaryEntry entry(
            final String term, @Nullable final String target, final TermType type, final Gender gender) {
        return GlossaryEntry.of(term, PROJECT, term, target, type, gender, false);
    }

    private static Window window(final List<WindowPair> previous, final @Nullable String next, final String... focus) {
        final List<FocusItem> items = new ArrayList<>();
        for (int index = 0; index < focus.length; index++) {
            items.add(new FocusItem("ch1:" + index, focus[index]));
        }
        return new Window(previous, new Focus(items, Map.of()), next);
    }

    private static MapLines select(
            final Window window, final List<GlossaryEntry> entries, final String source, final String target) {
        return select(window, entries, List.of(), source, target, List.of());
    }

    private static MapLines select(
            final Window window,
            final List<GlossaryEntry> entries,
            final List<LexiconEntry> terms,
            final String source,
            final String target,
            final List<ProtectedMask> masks) {
        final BookBrief brief = FrameFixtures.brief(source, target);
        return WindowMap.select(
                window, List.of(), BookMap.of(entries, terms, brief), masks, LexiconFilter.of(source, target), target);
    }

    @Test
    void inject_genitiveForm_bringsEntry() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Ірвін", "Irwin", TermType.CHARACTER, Gender.MALE).withForms(List.of("Ірвіна", "Ірвіну")),
                entry("Глафіра", "Hlafira", TermType.CHARACTER, Gender.FEMALE));

        final MapLines lines =
                select(window(List.of(), null, "Я дійшла до Ірвіна тільки вночі."), glossary, "uk", "en");

        assertThat(lines.glossary()).containsExactly("Ірвін → Irwin (character, male)");
    }

    @Test
    void inject_likelyGender_notSent() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Зотов", "Зотов", TermType.CHARACTER, Gender.UNKNOWN).withInferredGender(Gender.MALE),
                entry("Ивар", "Івар", TermType.CHARACTER, Gender.UNKNOWN).withSuggestedGender(Gender.MALE));

        final MapLines lines = select(window(List.of(), null, "Ивар ждал Зотова у ворот."), glossary, "ru", "uk");

        assertThat(lines.glossary()).containsExactly("Ивар → Івар (character)", "Зотов → Зотов (character, male)");
    }

    @Test
    void inject_place_noGender() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE),
                entry("Milton", "Мілтон", TermType.PLACE, Gender.MALE),
                entry("Baker Street", "Бейкер-стріт", TermType.PLACE, Gender.UNKNOWN));

        final MapLines lines = select(window(List.of(), null, "They reached Milton by noon."), glossary, "en", "uk");

        assertThat(lines.glossary()).containsExactly("Milton → Мілтон (place)");
        assertThat(lines.locked()).isEmpty();
        assertThat(lines.suggested()).isEmpty();
    }

    @Test
    void inject_lockedEntry_token() {
        final GlossaryEntry hale =
                GlossaryEntry.of("hale", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        final ProtectedMask mask = new ProtectedMask(
                "⟦g0⟧ opened the door.",
                List.of(new ProtectedSpan("⟦g0⟧", "Гейл", CheckName.LOCKED_TERM)),
                List.of(new LockedRendering("Hale", "Гейл")));
        final Window window =
                window(List.of(new WindowPair("ch0:0", "Hale waited outside.", null)), null, "⟦g0⟧ opened the door.");

        final MapLines lines = select(window, List.of(hale), List.of(), "en", "uk", List.of(mask));

        assertThat(lines.locked()).containsExactly("⟦g0⟧ → Гейл, character, male");
        assertThat(lines.glossary()).isEmpty();
    }

    @Test
    void inject_lockedEntryWithoutItsToken_leftOut() {
        final GlossaryEntry hale =
                GlossaryEntry.of("hale", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        final Window window =
                window(List.of(new WindowPair("ch0:0", "Hale waited outside.", null)), null, "The door opened.");

        final MapLines lines =
                select(window, List.of(hale), List.of(), "en", "uk", List.of(ProtectedMask.none("The door opened.")));

        assertThat(lines.isEmpty()).isTrue();
    }

    @Test
    void inject_termOnlyInOtherBatch_notInjected() {
        final List<GlossaryEntry> glossary = List.of(entry("Moreau", null, TermType.CHARACTER, Gender.MALE));
        final List<LexiconEntry> terms =
                List.of(LexiconEntry.of(PROJECT, "warden").withChosen("наглядач"));
        final Window window = window(
                List.of(new WindowPair("ch1:0", "The bell rang twice.", "Дзвін пролунав двічі.")),
                "Rain fell on the roofs.",
                "The gate was shut.");

        final MapLines lines = select(window, glossary, terms, "en", "uk", List.of());

        assertThat(lines.isEmpty()).isTrue();
    }

    @Test
    void inject_recurringTerm_establishedRenderingUnlessGlossaryHoldsIt() {
        final List<LexiconEntry> terms = List.of(
                LexiconEntry.of(PROJECT, "warden").withChosen("наглядач"),
                LexiconEntry.of(PROJECT, "lantern").withChosen("ліхтар"));
        final List<GlossaryEntry> glossary = List.of(entry("lantern", "Ліхтар", TermType.TERM, Gender.UNKNOWN));

        final MapLines lines = select(
                window(List.of(), "The warden lit a lantern.", "Nobody came."), glossary, terms, "en", "uk", List.of());

        assertThat(lines.lexicon()).containsExactly("warden → наглядач");
        assertThat(lines.glossary()).containsExactly("lantern → Ліхтар (term)");
    }

    @Test
    void inject_suggestedEntry_listedApart() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Hale", null, TermType.CHARACTER, Gender.MALE).withSuggestedTarget("Гейл"),
                entry("Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN),
                entry("Moreau", null, TermType.CHARACTER, Gender.MALE));

        final MapLines lines =
                select(window(List.of(), null, "Hale left Milton to find Moreau."), glossary, "en", "uk");

        assertThat(lines.glossary()).containsExactly("Milton → Мілтон (place)", "Moreau (character, male)");
        assertThat(lines.suggested()).containsExactly("Hale → Гейл (character, male)");
    }

    @Test
    void inject_proofreadTarget_readsTargetSide() {
        final List<GlossaryEntry> glossary = List.of(entry("Глафира", "Глафіра", TermType.CHARACTER, Gender.FEMALE));
        final BookBrief brief = FrameFixtures.brief("ru", "uk");

        final MapLines lines = WindowMap.select(
                window(List.of(), null, "Никто не пришёл."),
                List.of("Глафіру ніхто не чекав."),
                BookMap.of(glossary, List.of(), brief),
                List.of(),
                LexiconFilter.of("ru", "uk"),
                "uk");

        assertThat(lines.glossary()).containsExactly("Глафира → Глафіра (character, female)");
    }

    @Test
    void inject_glossChoice_onlyAtFirstUse() {
        final GlossaryEntry brightwater = entry("Brightwater", "Брайтвотер", TermType.PLACE, Gender.UNKNOWN)
                .withMeaning("bright water")
                .withMeaningChoice(MeaningChoice.TRANSCRIBE_GLOSS);
        final List<Unit> units = List.of(
                IndexTestBooks.unit("front", UnitRole.FRONT_MATTER, "Brightwater and Other Tales"),
                IndexTestBooks.unit(
                        "ch1", UnitRole.BODY, "Brightwater lay below the hill.", "They walked on to Brightwater."));
        final BookBrief brief = FrameFixtures.brief("en", "uk");
        final BookMap map = BookMap.of(List.of(brightwater), List.of(), brief, units);
        final Window first = new Window(
                List.of(),
                new Focus(List.of(new FocusItem("ch1:0", "Brightwater lay below the hill.")), Map.of()),
                "They walked on to Brightwater.");
        final Window later = new Window(
                List.of(new WindowPair("ch1:0", "Brightwater lay below the hill.", "Брайтвотер лежав під пагорбом.")),
                new Focus(List.of(new FocusItem("ch1:1", "They walked on to Brightwater.")), Map.of()),
                null);

        final MapLines atFirst = WindowMap.select(first, List.of(), map, List.of(), LexiconFilter.NONE, "uk");
        final MapLines afterIt = WindowMap.select(later, List.of(), map, List.of(), LexiconFilter.NONE, "uk");

        assertThat(map.glossFirstUse()).containsExactly(Map.entry("Brightwater", "ch1:0"));
        assertThat(atFirst.glossary()).containsExactly("Brightwater → Брайтвотер (place; gloss once: bright water)");
        assertThat(afterIt.glossary()).containsExactly("Brightwater → Брайтвотер (place)");
    }

    @ParameterizedTest(name = "[{index}] {0} in «{1}» -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Mura|He flew to Kito-Mura at dawn.|false",
                "Kito-Mura|He flew to Kito-Mura at dawn.|true",
                "Mura|He flew to Mura at dawn.|true",
                "Corvin|Corvin's hat blew off.|true",
                "Corvin|Both Corvins laughed.|true",
                "Corvin|Corvinian wine.|false",
                "Copper Kettle|The Copper  Kettle smiled.|true",
                "Case|He opened the case.|false",
                "Case|Case opened the door.|true",
                "Hale|The whale sang.|false",
                "Майлз|Майлза не було вдома.|true",
                "Майлз|Майлзові було байдуже.|false"
            })
    void inject_occurrence_followsTheOneMatcher(final String term, final String text, final boolean expected) {
        final List<GlossaryEntry> glossary = List.of(entry(term, "x", TermType.PLACE, Gender.UNKNOWN));

        final MapLines lines = select(window(List.of(), null, text), glossary, "en", "uk");

        assertThat(lines.glossary()).hasSize(expected ? 1 : 0);
    }

    // A locked entry with no target has no rendering to hide behind a token, so it is listed by its name.
    @Test
    void inject_entriesWithNoTarget_areListedByTheirNameAndFacts() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Moreau", null, TermType.CHARACTER, Gender.MALE),
                GlossaryEntry.of("quill", PROJECT, "Quill", null, TermType.CHARACTER, Gender.MALE, true));

        final MapLines lines = select(window(List.of(), null, "Moreau laughed at Quill."), glossary, "en", "uk");

        assertThat(lines.glossary()).containsExactly("Moreau (character, male)", "Quill (character, male)");
        assertThat(lines.locked()).isEmpty();
    }

    @Test
    void inject_termNextToAFootnoteMarker_isStillFound() {
        final List<GlossaryEntry> glossary = List.of(entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE));

        final MapLines lines = select(window(List.of(), null, "Hale⟦g0⟧1⟦g1⟧ opened the door."), glossary, "en", "uk");

        assertThat(lines.glossary()).containsExactly("Hale → Гейл (character, male)");
    }

    // A function word of the source and a one-word rendering in an oblique case are no renderings to keep.
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "like,подобатися,false",
        "just,просто,false",
        "program,програму,false",
        "program,програма,true",
        "console,консоль,true",
        "programs,програми,true"
    })
    void inject_recurringTerm_leavesOutFunctionWordsAndDeclinedForms(
            final String term, final String rendering, final boolean shown) {
        final List<LexiconEntry> terms = List.of(LexiconEntry.of(PROJECT, term).seen(rendering));

        final MapLines lines = select(
                window(List.of(), null, "The " + term + " was like that, just so."),
                List.of(),
                terms,
                "en",
                "uk",
                List.of());

        assertThat(lines.lexicon()).hasSize(shown ? 1 : 0);
    }

    @Test
    void lexiconLines_textsAndFormsOfTheTerm_listTheRenderingsTheGlossaryDoesNotHold() {
        final List<LexiconEntry> terms = List.of(
                new LexiconEntry(PROJECT, "warden", List.of(), "наглядач", null, null, null, List.of("wardens")),
                LexiconEntry.of(PROJECT, "lantern").withChosen("ліхтар"),
                LexiconEntry.of(PROJECT, "black").withChosen("чорний"),
                LexiconEntry.of(PROJECT, "bell"));
        final List<GlossaryEntry> glossary = List.of(entry("lantern", "Ліхтар", TermType.TERM, Gender.UNKNOWN));

        final List<String> lines = WindowMap.lexiconLines(
                List.of("Two wardens lit a lantern.", "A jet-black bell rang."), terms, glossary, LexiconFilter.NONE);

        assertThat(lines).containsExactly("warden → наглядач");
    }
}
