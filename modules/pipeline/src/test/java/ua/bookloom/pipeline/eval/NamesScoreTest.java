package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;

/**
 * The names score (15i.E1) on a glossary and lexicon built by hand against a gold built by hand, every expected
 * number written out: a gender counts for a character the book mentions five times, a wrong gender not marked
 * suggested is the verified kind, a cluster is consistent when its entries hold one gender, a junk term kept and a real
 * name lost are counted, and the recurring terms are counted by what the gold keeps.
 */
class NamesScoreTest {

    private static final String P = "p1";

    // Mentions: Dax Orlo 5, Dax 7 (it is a whole word inside "Dax Orlo"), Ysolde 5, Ysa 5, Valtern 6, Hale 2,
    // Perpetua 5, so Hale is the one gold character below the five-mention line.
    private static final List<String> BOOK = List.of(
            "Dax Orlo Dax Orlo Dax Orlo Dax Orlo Dax Orlo",
            "Dax Dax",
            "Ysolde Ysolde Ysolde Ysolde Ysolde",
            "Ysa Ysa Ysa Ysa Ysa",
            "Valtern Valtern Valtern Valtern Valtern Valtern",
            "Hale Hale",
            "Perpetua Perpetua Perpetua Perpetua Perpetua");

    private static final JudgementGold GOLD = new JudgementGold(
            null,
            "en",
            "uk",
            new JudgementGold.Brief(
                    null, List.of(), Register.NEUTRAL, null, null, null, NarratorPerson.THIRD, Gender.UNKNOWN),
            List.of(
                    new JudgementGold.Person("Dax Orlo", TermType.CHARACTER, Gender.MALE, "dax"),
                    new JudgementGold.Person("Dax", TermType.CHARACTER, Gender.MALE, "dax"),
                    new JudgementGold.Person("Ysolde", TermType.CHARACTER, Gender.FEMALE, "ysolde"),
                    new JudgementGold.Person("Ysa", TermType.CHARACTER, Gender.FEMALE, "ysolde"),
                    new JudgementGold.Person("Valtern", TermType.PLACE, Gender.UNKNOWN, null),
                    new JudgementGold.Person("Hale", TermType.CHARACTER, Gender.MALE, null),
                    new JudgementGold.Person("Perpetua", TermType.CHARACTER, Gender.FEMALE, null)),
            List.of("Mayday", "Orrin", "KESSEL OPTIK"),
            List.of("Orrin & Vale", "Velar", "Kessel Optik"),
            List.of(
                    new JudgementGold.Recurring("bankers", true),
                    new JudgementGold.Recurring("mark", false),
                    new JudgementGold.Recurring("guild", true)));

    private static GlossaryEntry entry(
            final String term, final String target, final TermType type, final Gender gender, final boolean suggested) {
        return new GlossaryEntry(
                P + ":" + term, P, term, target, type, gender, false, TargetOrigin.PERSON, suggested, false, List.of());
    }

    // Dax Orlo is right; Dax is a verified wrong gender and its own spelling of the name; Ysolde is wrong but still a
    // suggestion; Valtern is a person with a gender the book never gave a city; Hale has the wrong type; Perpetua and
    // Ysa have no entry; Mayday is junk that stayed; Velar is only an alias, which still holds the name; "Kessel
    // Optik" is the real name, and the junk "KESSEL OPTIK" is a separate entry that is not there.
    private static List<GlossaryEntry> glossary() {
        return List.of(
                entry("Dax Orlo", "Дакс Орло", TermType.CHARACTER, Gender.MALE, false),
                entry("Dax", "Декс", TermType.CHARACTER, Gender.FEMALE, false),
                entry("Ysolde", "Ізольда", TermType.CHARACTER, Gender.MALE, true),
                entry("Valtern", "Валтерн", TermType.CHARACTER, Gender.FEMALE, false),
                entry("Hale", null, TermType.TERM, Gender.MALE, false),
                entry("Mayday", null, TermType.OTHER, Gender.UNKNOWN, false),
                entry("Kessel Optik", null, TermType.OTHER, Gender.UNKNOWN, false),
                new GlossaryEntry(
                        P + ":Velaris",
                        P,
                        "Velaris",
                        null,
                        TermType.PLACE,
                        Gender.UNKNOWN,
                        false,
                        TargetOrigin.PERSON,
                        false,
                        false,
                        List.of("Velar")));
    }

    private static GlossaryEntry withAlias(
            final String term, final String target, final TermType type, final String alias) {
        return new GlossaryEntry(
                P + ":" + term,
                P,
                term,
                target,
                type,
                Gender.FEMALE,
                false,
                TargetOrigin.PERSON,
                false,
                false,
                List.of(alias));
    }

    private static List<LexiconEntry> lexicon() {
        return List.of(LexiconEntry.of(P, "bankers"), LexiconEntry.of(P, "mark"));
    }

    private static NamesScore score() {
        return NamesScore.of(GOLD, BOOK, glossary(), lexicon(), 12, Duration.ofSeconds(90));
    }

    @Test
    void of_charactersMentionedFiveTimes_countGenderCorrectAndWrongButVerified() {
        final NamesScore score = score();

        // Eligible: Dax Orlo, Dax, Ysolde, Ysa, Valtern, Perpetua. Right: Dax Orlo. Wrong with a verified (unsuggested)
        // gender: Dax and Valtern; Ysolde's wrong gender is only suggested, and Ysa and Perpetua have no entry.
        assertThat(score.eligible()).isEqualTo(6);
        assertThat(score.genderCorrect()).isEqualTo(1);
        assertThat(score.wrongButVerified()).isEqualTo(2);
        assertThat(score.genderAccuracy()).isEqualTo(1.0 / 6);
    }

    @Test
    void of_everyGoldCharacter_isScoredForTypeAndAMissingEntryIsWrong() {
        final NamesScore score = score();

        assertThat(score.typeScored()).isEqualTo(7);
        assertThat(score.typeCorrect()).isEqualTo(3);
        assertThat(score.typeAccuracy()).isEqualTo(3.0 / 7);
    }

    @Test
    void of_clustersOfTwoOrMoreMembers_areConsistentWhenTheirEntriesHoldOneGender() {
        final NamesScore score = score();

        // dax holds MALE and FEMALE; ysolde holds only Ysolde's MALE since Ysa has no entry.
        assertThat(score.clusters()).isEqualTo(2);
        assertThat(score.clustersWithOneGender()).isEqualTo(1);
        assertThat(score.clusterRate()).isEqualTo(0.5);
    }

    @Test
    void of_clusterTargets_countDistinctFirstWordsOfTheName() {
        final NamesScore score = score();

        // dax: "Дакс Орло" and "Декс" are two spellings of the name; ysolde: one.
        assertThat(score.spellings()).containsOnly(Map.entry("dax", 2), Map.entry("ysolde", 1));
        assertThat(score.maxSpellings()).isEqualTo(2);
    }

    @Test
    void of_junkAndRealNames_countJunkKeptAndNamesLostThroughAliasesToo() {
        final NamesScore score = score();

        // Mayday stayed; Orrin and the capitals-only "KESSEL OPTIK" are not there (junk is matched exactly, so the real
        // "Kessel Optik" does not count). Lost: Ysa, Perpetua and "Orrin & Vale"; Velar is held as an alias and
        // a name that is both a character and a keep term counts once.
        assertThat(score.junkKept()).isEqualTo(1);
        assertThat(score.lostNames()).containsExactly("Ysa", "Perpetua", "Orrin & Vale");
        assertThat(score.realNamesLost()).isEqualTo(3);
    }

    @Test
    void of_goldCharacterHeldOnlyAsAnAliasOfAnotherEntry_isScoredFromThatEntry() {
        final JudgementGold gold = new JudgementGold(
                null,
                "en",
                "uk",
                GOLD.brief(),
                List.of(
                        new JudgementGold.Person("Ysolde", TermType.CHARACTER, Gender.FEMALE, "ysolde"),
                        new JudgementGold.Person("Ysa", TermType.CHARACTER, Gender.FEMALE, "ysolde")),
                List.of(),
                List.of(),
                List.of());
        final List<GlossaryEntry> glossary = List.of(withAlias("Ysolde", "Ізольда", TermType.CHARACTER, "Ysa"));

        final NamesScore score = NamesScore.of(gold, BOOK, glossary, List.of(), 0, Duration.ZERO);

        assertThat(score.eligible()).isEqualTo(2);
        assertThat(score.genderCorrect()).isEqualTo(2);
        assertThat(score.typeCorrect()).isEqualTo(2);
        assertThat(score.clustersWithOneGender()).isEqualTo(1);
        assertThat(score.spellings()).containsOnly(Map.entry("ysolde", 1));
        assertThat(score.realNamesLost()).isZero();
    }

    @Test
    void of_nameThatIsBothACharacterAndAKeepTerm_isLostOnce() {
        final JudgementGold gold = new JudgementGold(
                null,
                "en",
                "uk",
                GOLD.brief(),
                List.of(new JudgementGold.Person("Perpetua", TermType.CHARACTER, Gender.FEMALE, null)),
                List.of(),
                List.of("Perpetua"),
                List.of());

        final NamesScore score = NamesScore.of(gold, BOOK, List.of(), List.of(), 0, Duration.ZERO);

        assertThat(score.lostNames()).containsExactly("Perpetua");
    }

    @Test
    void of_recurringTerms_areCountedByWhatTheGoldKeeps() {
        final NamesScore score = score();

        // bankers kept as it should; mark kept but the gold drops it; guild is missing but the gold keeps it.
        assertThat(score.recurringKept()).isEqualTo(1);
        assertThat(score.recurringJunkKept()).isEqualTo(1);
        assertThat(score.recurringDropped()).isEqualTo(1);
    }

    @Test
    void of_callsAndElapsedTime_areCarriedAsCallsAndMinutes() {
        final NamesScore score = score();

        assertThat(score.calls()).isEqualTo(12);
        assertThat(score.minutes()).isEqualTo(1.5);
    }

    @Test
    void of_wrongGlossary_isNotClean() {
        assertThat(score().isClean()).isFalse();
    }

    @Test
    void of_glossaryThatMatchesTheGold_isCleanWithFullAccuracies() {
        final JudgementGold gold = new JudgementGold(
                null,
                "en",
                "uk",
                GOLD.brief(),
                List.of(
                        new JudgementGold.Person("Dax Orlo", TermType.CHARACTER, Gender.MALE, "dax"),
                        new JudgementGold.Person("Dax", TermType.CHARACTER, Gender.MALE, "dax")),
                List.of("Mayday"),
                List.of("Velar"),
                List.of(new JudgementGold.Recurring("bankers", true)));
        final List<GlossaryEntry> glossary = List.of(
                entry("Dax Orlo", "Дакс Орло", TermType.CHARACTER, Gender.MALE, false),
                entry("Dax", "Дакс", TermType.CHARACTER, Gender.MALE, true),
                entry("Velar", "Велар", TermType.PLACE, Gender.UNKNOWN, false));

        final NamesScore score =
                NamesScore.of(gold, BOOK, glossary, List.of(LexiconEntry.of(P, "Bankers")), 3, Duration.ofSeconds(30));

        assertThat(score.genderAccuracy()).isEqualTo(1.0);
        assertThat(score.typeAccuracy()).isEqualTo(1.0);
        assertThat(score.clusterRate()).isEqualTo(1.0);
        assertThat(score.spellings()).containsOnly(Map.entry("dax", 1));
        assertThat(score.wrongButVerified()).isZero();
        assertThat(score.junkKept()).isZero();
        assertThat(score.realNamesLost()).isZero();
        assertThat(score.isClean()).isTrue();
    }

    @Test
    void of_goldWithNothingToScore_givesFullRatesAndNoSpellings() {
        final JudgementGold empty =
                new JudgementGold(null, "en", "uk", GOLD.brief(), List.of(), List.of(), List.of(), List.of());

        final NamesScore score = NamesScore.of(empty, BOOK, List.of(), List.of(), 0, Duration.ZERO);

        assertThat(score.genderAccuracy()).isEqualTo(1.0);
        assertThat(score.typeAccuracy()).isEqualTo(1.0);
        assertThat(score.clusterRate()).isEqualTo(1.0);
        assertThat(score.maxSpellings()).isZero();
        assertThat(score.isClean()).isTrue();
    }

    @Test
    void json_score_isTheNumbersWithClusterIdsSortedAndRatesToThreeDecimals() {
        assertThat(score().json())
                .isEqualTo("{\"eligible\":6,\"genderCorrect\":1,\"wrongButVerified\":2,\"genderAccuracy\":0.167,"
                        + "\"typeCorrect\":3,\"typeScored\":7,\"typeAccuracy\":0.429,\"clusters\":2,"
                        + "\"clustersWithOneGender\":1,\"clusterRate\":0.5,\"spellings\":{\"dax\":2,\"ysolde\":1},"
                        + "\"junkKept\":1,\"realNamesLost\":3,\"recurringKept\":1,\"recurringDropped\":1,"
                        + "\"recurringJunkKept\":1,\"calls\":12,\"minutes\":1.5}");
    }
}
