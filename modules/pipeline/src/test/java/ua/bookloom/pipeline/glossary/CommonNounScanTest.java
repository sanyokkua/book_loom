package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;

/**
 * The name scan's reading of how a book writes a capitalised word (15i.N5 junk, 15i.N8 lost names): a word written
 * after an article in nearly every use is a common noun or an adjective when the book also writes it before a noun
 * after "a" (a nationality), when the pronoun that follows it is "they" (a group), or when it is the plural of another
 * capitalised word (a family, a brand's plural); a name written truly alone twice is a name of its own beside its
 * full name; and two names joined by "of" are one name.
 */
class CommonNounScanTest {

    private static final int THRESHOLD = 3;
    private static final List<String> PERSON = copies("We saw Ilsabet at the gate.", 4);

    @Test
    void candidates_adjectiveWrittenAfterArticlesAndBeforeANoun_isNotProposed() {
        final List<String> lines = new ArrayList<>(PERSON);
        lines.add("Then a Morvish porter carried the bag.");
        lines.add("Then a Morvish child brought bread.");
        lines.add("We ate with the Morvish at noon.");
        lines.add("We sat near the Morvish again.");

        assertThat(terms(lines)).containsExactly("Ilsabet");
    }

    // "a Veiling on the door": the word after it is a function word, so the spell is not used as an adjective.
    @Test
    void candidates_nounCastWithAnArticleBeforeAFunctionWord_staysProposed() {
        final List<String> lines = new ArrayList<>(PERSON);
        lines.addAll(copies("He cast a Veiling on the door.", 2));
        lines.addAll(copies("We feared the Veiling then.", 2));

        assertThat(terms(lines)).containsExactlyInAnyOrder("Ilsabet", "Veiling");
    }

    @Test
    void candidates_groupWordFollowedByThey_isNotProposed() {
        final List<String> lines = new ArrayList<>(PERSON);
        lines.add("In the square the Lantermen kept to their side. They wore grey coats.");
        lines.add("At night the Lantermen came to the inn. They asked about the letter.");
        lines.add("Later the Lantermen left the town.");

        assertThat(terms(lines)).containsExactly("Ilsabet");
    }

    // A gang's name after "the" with no "they" and no singular stays a name.
    @Test
    void candidates_pluralNameWithNoSingularAndNoThey_staysProposed() {
        final List<String> lines = new ArrayList<>(PERSON);
        lines.addAll(copies("She was one of the Larkfeathers, a member of the crew.", 3));

        assertThat(terms(lines)).containsExactlyInAnyOrder("Ilsabet", "Larkfeathers");
    }

    @Test
    void newTerms_pluralWrittenAfterArticles_isAnAliasOfItsSingular() {
        final List<String> lines = new ArrayList<>(copies("We saw Hollin at noon.", 6));
        lines.addAll(copies("We visited the Hollins at home.", 4));

        assertThat(entries(lines))
                .extracting(GlossaryEntry::term, GlossaryEntry::aliases)
                .containsExactly(tuple("Hollin", List.of("Hollins")));
    }

    // The plural of the first word of a brand ("Velk Optik"), written after articles, is not a name of its own.
    @Test
    void candidates_pluralOfAWordInsideALongerName_isNotProposed() {
        final List<String> lines = new ArrayList<>(copies("Then she used the Velk Optik again.", 4));
        lines.add("Every Velks jammed in the rain.");
        lines.add("She preferred her Velks to the rest.");
        lines.add("The clerk praised the Velks at length.");

        assertThat(terms(lines)).containsExactly("Velk Optik");
    }

    // 15i.N8: a surname written alone, away from a sentence start and any other capital, twice is a name of its own.
    @Test
    void candidates_surnameWrittenTrulyAloneTwice_isProposedBesideTheFullName() {
        final List<String> lines = new ArrayList<>(copies("Then Orrel Danby sang.", 30));
        lines.addAll(copies("We saw Danby at noon.", 2));
        lines.add("Danby nodded.");

        assertThat(terms(lines)).containsExactly("Orrel Danby", "Danby");
    }

    @Test
    void candidates_namesJoinedByOf_isOneName() {
        final List<String> lines = new ArrayList<>(copies("We rode to the Tower of Arlen.", 3));
        lines.addAll(copies("Then the Keeper of the Grey Gate spoke.", 3));

        assertThat(terms(lines)).contains("Tower of Arlen", "Keeper of the Grey Gate");
    }

    @Test
    void candidates_ofBeforeALowerCaseWord_joinsNothing() {
        final List<String> lines = new ArrayList<>(copies("We saw Hale of course.", 3));

        assertThat(terms(lines)).containsExactly("Hale");
    }

    private static List<String> terms(final List<String> lines) {
        final BookSlice book = GlossaryTestSegments.of(lines);
        return FrequencyScan.candidates(book.index(), book.ordinals(), THRESHOLD, "en").stream()
                .map(NameCandidate::term)
                .toList();
    }

    private static List<GlossaryEntry> entries(final List<String> lines) {
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
        final BookSlice book = GlossaryTestSegments.of(lines);
        return Objects.requireNonNull(
                FrequencyScan.newTerms("p1", book.index(), book.ordinals(), "en", glossary)
                        .data(),
                "entries");
    }

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }
}
