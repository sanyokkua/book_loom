package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;

/**
 * The autonomous names pass on an invented book (15i.N8): the steps the Names &amp; style buttons run, in order, over a
 * staged copy of both tables, one write at the end, the person's own entries and removals left alone. The model is a
 * hand-written one that answers each call by its response format; the stored tables are real (the in-memory
 * adapters).
 */
class NamesPassImplTest extends NamesPassTestBase {

    // IF a step wrote as it went, THEN a call's failure or a Stop later in the pass would leave half a result; every
    // call goes out against the tables exactly as the person left them.
    @Test
    void run_seededBook_fillsBothTablesAndStoresNothingBeforeTheEnd() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final List<GlossaryEntry> glossaryBefore = fixture.glossaryNow();
        final List<LexiconEntry> lexiconBefore = fixture.lexiconNow();

        succeeded(run(model()));

        assertThat(glossarySeen)
                .isNotEmpty()
                .allSatisfy(seen -> assertThat(seen).isEqualTo(glossaryBefore));
        assertThat(lexiconSeen).isNotEmpty().allSatisfy(seen -> assertThat(seen).isEqualTo(lexiconBefore));
        assertThat(fixture.glossaryNow()).isNotEqualTo(glossaryBefore);
        assertThat(fixture.lexiconNow()).isNotEqualTo(lexiconBefore);
    }

    // Names are typed, gendered by the book's pronouns and given a suggested target; a word that is no name is gone.
    // The pseudo model quotes no pronoun, so the pronouns' gender is a suggestion (15i.N gate).
    @Test
    void run_seededBook_typesGendersAndTargetsTheNamesAndRemovesWhatIsNoName() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        succeeded(run(model()));

        assertThat(fixture.glossaryNow())
                .extracting(GlossaryEntry::term)
                .doesNotContain("Overture")
                .contains("Brann", "Harrowgate", "Zorbo", "Silverbrook");
        assertThat(held("Brann"))
                .extracting(
                        GlossaryEntry::type,
                        GlossaryEntry::gender,
                        GlossaryEntry::genderOrigin,
                        GlossaryEntry::target,
                        GlossaryEntry::origin,
                        GlossaryEntry::locked)
                .containsExactly(
                        TermType.CHARACTER,
                        Gender.MALE,
                        GenderOrigin.SUGGESTED,
                        "Бранн",
                        TargetOrigin.SUGGESTED,
                        false);
        assertThat(held("Harrowgate"))
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(TermType.PLACE, Gender.UNKNOWN, "Гарроугейт", false);
        assertThat(held("Silverbrook"))
                .extracting(GlossaryEntry::type, GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(TermType.PLACE, "Срібний Струмок", false);
    }

    // A brand is kept as written and locked whatever the target does to names; a name in an inflecting target is not.
    @Test
    void run_ukrainianTarget_locksTheBrandKeptAsWrittenAndNoOtherName() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        succeeded(run(model()));

        assertThat(fixture.glossaryNow())
                .filteredOn(GlossaryEntry::locked)
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactlyInAnyOrder(tuple("Zorbo", "Zorbo"), tuple("Gatewarden", "Брамник"));
    }

    // German does not decline names: a name read as a name, not a speaking one, rendered and checked, is locked.
    @Test
    void run_germanTarget_locksVerifiedNonSpeakingNamesAndLeavesTheSpeakingOneOpen() {
        seeded("de", NamePolicy.TRANSLITERATE);

        succeeded(run(model().suggests("Brann", "Brann")
                .suggests("Wren", "Wren")
                .suggests("Harrowgate", "Harrowgate")
                .suggests("Silverbrook", "Silberbach")
                .suggests("wardstone", "Wardstein")
                .suggests("beacon", "Leuchtfeuer")));

        assertThat(fixture.glossaryNow())
                .filteredOn(GlossaryEntry::locked)
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactlyInAnyOrder(
                        tuple("Brann", "Brann"),
                        tuple("Wren", "Wren"),
                        tuple("Harrowgate", "Harrowgate"),
                        tuple("Zorbo", "Zorbo"),
                        tuple("Gatewarden", "Брамник"));
        assertThat(held("Silverbrook").target()).isEqualTo("Silberbach");
        assertThat(held("wardstone").locked()).isFalse();
    }

    // Under Keep original a name keeps its source spelling, which is the kept-verbatim ground whatever the target.
    @Test
    void run_keepOriginalPolicy_locksEveryNameKeptAsWritten() {
        seeded("uk", NamePolicy.KEEP_ORIGINAL);

        succeeded(run(model()));

        assertThat(fixture.glossaryNow())
                .filteredOn(GlossaryEntry::locked)
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactlyInAnyOrder(
                        tuple("Brann", "Brann"),
                        tuple("Wren", "Wren"),
                        tuple("Harrowgate", "Harrowgate"),
                        tuple("Zorbo", "Zorbo"),
                        tuple("Silverbrook", "Silverbrook"),
                        tuple("Gatewarden", "Брамник"));
    }

    // The recurring terms: a word used in one sense is kept and given a rendering, an ordinary word is dropped, a
    // named thing moves to the glossary and is rendered there.
    @Test
    void run_seededBook_keepsDropsAndPromotesTheRecurringTerms() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        succeeded(run(model()));

        assertThat(fixture.lexiconNow())
                .extracting(LexiconEntry::term, entry -> entry.established().orElse(null))
                .containsExactlyInAnyOrder(tuple("anvil", "ковадло"), tuple("beacon", "маяк"));
        assertThat(held("wardstone"))
                .extracting(GlossaryEntry::type, GlossaryEntry::target, GlossaryEntry::origin)
                .containsExactly(TermType.TERM, "вартовий камінь", TargetOrigin.SUGGESTED);
    }

    // The report is the change against the tables as they were, so a screen can show and undo each row.
    @Test
    void run_seededBook_reportsEachTableAgainstItsStateBeforeThePass() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        final NamesPassReport report = succeeded(run(model()));

        assertThat(report.names().added()).extracting(GlossaryEntry::term).containsExactly("wardstone");
        assertThat(report.names().removed())
                .extracting(removal -> removal.entry().term())
                .containsExactly("Overture");
        assertThat(report.names().changed())
                .extracting(change -> change.before().term())
                .containsExactlyInAnyOrder("Brann", "Wren", "Harrowgate", "Zorbo", "Silverbrook");
        assertThat(report.terms().added()).extracting(LexiconEntry::term).containsExactly("beacon");
        assertThat(report.terms().removed())
                .extracting(removal -> removal.entry().term())
                .containsExactly("ferry");
        assertThat(report.terms().changed()).isEmpty();
    }

    // The clock is the pass's own: two seconds per call (the model moves it) is two seconds per call in the report.
    @Test
    void run_seededBook_reportsTheCallsMadeAndTheTimeTheyTook() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final NamesPassModel model = model();

        final NamesPassReport report = succeeded(run(model));

        assertThat(report.calls()).isEqualTo(model.requests()).isGreaterThan(5);
        assertThat(report.elapsed()).isEqualTo(PER_CALL.multipliedBy(model.requests()));
    }

    // Every call is announced to the screen once, so its live panel can show them.
    @Test
    void run_seededBook_announcesEveryModelCallToTheProgressReceiver() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final NamesPassModel model = model();

        succeeded(run(model));

        assertThat(events.stream().filter(ModelCallStarted.class::isInstance).count())
                .isEqualTo(model.requests());
    }

    // The steps are the buttons' in the buttons' order: the names are read and decided, then the recurring terms are
    // read, and only then are both tables rendered.
    @Test
    void run_seededBook_readsTheNamesThenTheTermsThenTranslatesBoth() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final NamesPassModel model = model();

        succeeded(run(model));

        final List<String> formats = model.formats();
        assertThat(formats).contains("term-reading", "review-terms", "lexicon-reading", "suggest-targets");
        assertThat(formats.lastIndexOf("term-reading")).isLessThan(formats.indexOf("lexicon-reading"));
        assertThat(formats.lastIndexOf("review-terms")).isLessThan(formats.indexOf("lexicon-reading"));
        assertThat(formats.lastIndexOf("lexicon-reading")).isLessThan(formats.indexOf("suggest-targets"));
        assertThat(model.kinds())
                .contains(CallKind.TERM_READING, CallKind.REVIEW_TERMS, CallKind.SUGGEST_TARGETS)
                .doesNotContain(CallKind.DRAFT);
    }

    // The person's own work: a locked office and a street with its target are not asked, not suggested over and not
    // reported; a woman the person set stays a woman though every pronoun the book gives her is masculine; a word
    // with a rendering the person typed is not asked about.
    @Test
    void run_personsEntries_areLeftAsTheyWere() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final GlossaryEntry office = held("Gatewarden");
        final GlossaryEntry street = held("Marrow Lane");
        final LexiconEntry anvil = fixture.lexiconNow().stream()
                .filter(entry -> entry.term().equals("anvil"))
                .findFirst()
                .orElseThrow();

        final NamesPassReport report = succeeded(run(model().usesWord("anvil", "word")));

        assertThat(fixture.glossaryNow()).contains(office, street);
        assertThat(fixture.lexiconNow()).contains(anvil);
        assertThat(held("Wren"))
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(TermType.CHARACTER, Gender.FEMALE, GenderOrigin.PERSON);
        assertThat(report.names().changed())
                .extracting(change -> change.before().term())
                .doesNotContain("Gatewarden", "Marrow Lane");
        assertThat(report.terms().removed())
                .extracting(removal -> removal.entry().term())
                .doesNotContain("anvil");
    }

    // 15i.N5: a person the book gives no pronoun, form of address or verified vote for stays "not specified", and the
    // entry says the scan found no evidence; a person with a pronoun case is gendered and not marked.
    @Test
    void run_personWithNoPronounInTheBook_staysUnknownAndIsMarkedAsNoEvidence() {
        fixture.open(NamesPassFixture.brief("uk", NamePolicy.TRANSLITERATE), odoLines());
        fixture.seedOpenNames().seedPersonsEntries().seedTerms();
        fixture.glossary.add(NamesPassFixture.untyped("Odo"));

        succeeded(run(model().reads("Odo", "person", false, false).suggests("Odo", "Одо")));

        assertThat(held("Odo"))
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::noGenderEvidence)
                .containsExactly(TermType.CHARACTER, Gender.UNKNOWN, true);
        assertThat(held("Brann").noGenderEvidence()).isFalse();
    }

    // The person's edit of the unmarked-by-scan gender wins over the mark: setting a gender clears it.
    @Test
    void run_markedPersonThenEditedByThePerson_isNoLongerMarked() {
        fixture.open(NamesPassFixture.brief("uk", NamePolicy.TRANSLITERATE), odoLines());
        fixture.seedOpenNames().seedPersonsEntries().seedTerms();
        fixture.glossary.add(NamesPassFixture.untyped("Odo"));
        succeeded(run(model().reads("Odo", "person", false, false).suggests("Odo", "Одо")));

        fixture.glossary.update(held("Odo").withGender(Gender.MALE));

        assertThat(held("Odo"))
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin, GlossaryEntry::noGenderEvidence)
                .containsExactly(Gender.MALE, GenderOrigin.PERSON, false);
    }

    private static List<String> odoLines() {
        return List.of(
                "Odo sorted the letters.",
                "Odo swept the cellar.",
                "Odo hummed by the window.",
                "Odo counted the candles.",
                "Odo waited at the door.",
                "Odo watched the road.");
    }

    // The glossary scan runs only on an empty glossary, as the Scan button does: a glossary that holds names is not
    // scanned for more.
    @Test
    void run_glossaryAlreadyHoldsNames_addsNoNameTheBookWrites() {
        fixture.open(NamesPassFixture.brief("uk", NamePolicy.TRANSLITERATE));
        fixture.glossary.add(NamesPassFixture.untyped("Brann"));

        succeeded(run(model()));

        assertThat(fixture.glossaryNow())
                .extracting(GlossaryEntry::term)
                .containsExactlyInAnyOrder("Brann", "wardstone");
    }

    // An empty glossary is scanned first, and what the scan found goes through the same reading and rendering.
    @Test
    void run_emptyGlossary_scansTheBookThenReadsAndRendersWhatItFound() {
        fixture.open(NamesPassFixture.brief("uk", NamePolicy.TRANSLITERATE));

        final NamesPassReport report = succeeded(run(model()));

        assertThat(report.names().added())
                .extracting(GlossaryEntry::term)
                .contains("Brann", "Wren", "Harrowgate", "Zorbo", "Silverbrook", "Gatewarden", "Marrow Lane");
        assertThat(held("Harrowgate"))
                .extracting(GlossaryEntry::type, GlossaryEntry::target)
                .containsExactly(TermType.PLACE, "Гарроугейт");
    }

    // IF the scan proposed a name the person removed, THEN the pass would undo the removal; the glossary remembers it.
    @Test
    void run_nameThePersonRemoved_isNotScannedBack() {
        fixture.open(NamesPassFixture.brief("uk", NamePolicy.TRANSLITERATE));
        fixture.glossary.add(NamesPassFixture.untyped("Brann"));
        fixture.glossary.remove(PROJECT, held("Brann").id());

        succeeded(run(model()));

        assertThat(fixture.glossaryNow())
                .extracting(GlossaryEntry::term)
                .doesNotContain("Brann")
                .contains("Harrowgate");
    }

    // A term the person removed from the recurring terms stays removed, though the model would keep the word.
    @Test
    void run_termThePersonRemoved_isNotAddedBack() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        fixture.lexicon.put(LexiconEntry.of(PROJECT, "gantry"));
        fixture.lexicon.remove(PROJECT, "gantry");

        final NamesPassReport report = succeeded(run(model().usesWord("gantry", "term")));

        assertThat(fixture.lexiconNow()).extracting(LexiconEntry::term).doesNotContain("gantry");
        assertThat(report.terms().added()).extracting(LexiconEntry::term).doesNotContain("gantry");
    }
}
