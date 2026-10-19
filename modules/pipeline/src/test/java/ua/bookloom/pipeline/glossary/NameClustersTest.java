package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;

/**
 * Which glossary entries name one person (15i.N2): a one-word entry that is the first word of exactly one multi-word
 * character entry, or its last word, joins it; an honorific form joins the unique character with its surname; any
 * other shape stays alone. The glossary is written as {@code term=K} entries with K a type letter: C character,
 * O untyped, P place, T term; an optional second letter sets the gender, m male or f female.
 */
class NameClustersTest {

    private static final String PROJECT = "p1";

    // The cluster of the subject entry, in glossary order, for each shape of glossary the rule has to tell apart.
    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                // glossary | subject | members of its cluster, glossary order
                "Arlo=C, Venn=C, Arlo Venn=C | Arlo | Arlo, Venn, Arlo Venn",
                "Arlo=C, Venn=C, Arlo Venn=C | Venn | Arlo, Venn, Arlo Venn",
                "Arlo=C, Venn=C, Arlo Venn=C | Arlo Venn | Arlo, Venn, Arlo Venn",
                // two people share a surname: the surname alone is ambiguous, each first name is not
                "Arlo Venn=C, Mira Venn=C, Venn=C, Arlo=C, Mira=C | Venn | Venn",
                "Arlo Venn=C, Mira Venn=C, Venn=C, Arlo=C, Mira=C | Arlo | Arlo Venn, Arlo",
                "Arlo Venn=C, Mira Venn=C, Venn=C, Arlo=C, Mira=C | Mira | Mira Venn, Mira",
                "Arlo Venn=C, Mira Venn=C, Venn=C, Arlo=C, Mira=C | Mira Venn | Mira Venn, Mira",
                // an honorific form joins the one full name with its surname when the honorific's gender, if it has
                // one, agrees with that name's gender (Cm: a male character, Cf: a female one)
                "Mr Venn=C, Arlo Venn=Cm | Mr Venn | Mr Venn, Arlo Venn",
                "Mr. Venn=C, Arlo Venn=Cm | Mr. Venn | Mr. Venn, Arlo Venn",
                "Mrs Venn=C, Mira Venn=Cf | Mrs Venn | Mrs Venn, Mira Venn",
                "Miss Venn=C, Mira Venn=Cf | Mira Venn | Miss Venn, Mira Venn",
                "Dr Venn=C, Arlo Venn=Cm | Dr Venn | Dr Venn, Arlo Venn",
                "Dr Venn=C, Mira Venn=Cf | Dr Venn | Dr Venn, Mira Venn",
                "Dr Venn=C, Mira Venn=C | Dr Venn | Dr Venn, Mira Venn",
                "Mr Venn=C, Arlo Venn=Cm, Mira Venn=Cf | Mr Venn | Mr Venn",
                // a gendered honorific never joins a name of the other gender (Mrs Venn is often a man's wife) or of
                // an unknown one: the two are then two people
                "Mrs Venn=C, Arlo Venn=Cm | Mrs Venn | Mrs Venn",
                "Mrs Venn=C, Arlo Venn=Cm | Arlo Venn | Arlo Venn",
                "Miss Venn=C, Arlo Venn=Cm | Miss Venn | Miss Venn",
                "Ms Venn=C, Arlo Venn=Cm | Ms Venn | Ms Venn",
                "Mr Venn=C, Mira Venn=Cf | Mr Venn | Mr Venn",
                "Mrs Venn=C, Arlo Venn=C | Mrs Venn | Mrs Venn",
                "Mr Venn=C, Arlo Venn=C | Mr Venn | Mr Venn",
                // an honorific form is no anchor: the bare surname still finds the one full name
                "Venn=C, Mr Venn=C, Arlo Venn=Cm | Venn | Venn, Mr Venn, Arlo Venn",
                "Venn=C, Mr Venn=C | Venn | Venn",
                "Venn=C, Mr Venn=C | Mr Venn | Mr Venn",
                // a man's form of address beside a woman's full name of that surname (a married couple): the bare
                // surname may be either, so it joins neither (15i.N gate: a wife's cluster gave her husband's
                // surname her gender, verified)
                "Venn=O, Mira Venn=Cf, Mrs Venn=C, Mr Venn=C | Venn | Venn",
                "Venn=O, Mira Venn=Cf, Mrs Venn=C | Venn | Venn, Mira Venn, Mrs Venn",
                // a place or a thing is never joined, and never counts as a second person
                "Venn=C, Venn Hall=P | Venn | Venn",
                "Venn=C, Arlo Venn=C, Venn Hall=P | Venn | Venn, Arlo Venn",
                "Venn=P, Arlo Venn=C | Venn | Venn",
                "Venn=T, Arlo Venn=C | Venn | Venn",
                "Venn=O, Arlo Venn=C | Venn | Venn, Arlo Venn",
                "Arlo=C, Arlo Venn=O | Arlo | Arlo",
                // the word must be the first or the last, and only one full name may hold it
                "Dane=C, Arlo Dane Venn=C | Dane | Dane",
                "Arlo=C, Arlo Venn=C, Mira Arlo=C | Arlo | Arlo",
                "Arlo=C, Arlo Venn=C, Arlo Dray=C | Arlo | Arlo",
                "Arlo=C | Arlo | Arlo",
                // only a one-word entry (or an honorific form) joins: two multi-word names never join each other
                "Arlo Venn=C, Arlo Venn Dray=C | Arlo Venn | Arlo Venn"
            })
    void membersOf_glossaryShape_joinsOnlyTheUniqueFullName(
            final String glossary, final String subject, final String expected) {
        final List<GlossaryEntry> entries = entries(glossary);

        final NameClusters clusters = NameClusters.of(entries, null);

        assertThat(clusters.membersOf(named(entries, subject)))
                .extracting(GlossaryEntry::term)
                .containsExactlyElementsOf(terms(expected));
    }

    // The first-name list of the book's language decides for a full name with no gender of its own, so Mrs Venn joins
    // Mary Venn and never John Venn; with no language the list is silent and an unknown gender keeps them apart.
    @ParameterizedTest(name = "{0}: {1} in {3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Mr Venn=C, John Venn=C | Mr Venn | Mr Venn, John Venn | en",
                "Mrs Venn=C, John Venn=C | Mrs Venn | Mrs Venn | en",
                "Mrs Venn=C, Mary Venn=C | Mrs Venn | Mrs Venn, Mary Venn | en",
                "Mrs Venn=C, Mary Venn=C | Mrs Venn | Mrs Venn | de",
                "Mrs Venn=C, Mary Venn=Cm | Mrs Venn | Mrs Venn | en"
            })
    void membersOf_honorificAndAFullNameWithNoGender_followsTheGivenNameList(
            final String glossary, final String subject, final String expected, final String language) {
        final List<GlossaryEntry> entries = entries(glossary);

        final NameClusters clusters = NameClusters.of(entries, null, language);

        assertThat(clusters.membersOf(named(entries, subject)))
                .extracting(GlossaryEntry::term)
                .containsExactlyElementsOf(terms(expected));
    }

    // IF membership asked the book, THEN the glossary service, which has no index when no book is open, could not
    // propagate a person's edit.
    @Test
    void membersOf_withAndWithoutABookIndex_isTheSameCluster() {
        final List<GlossaryEntry> entries = entries("Arlo=C, Venn=C, Arlo Venn=C, Mira Venn=C");
        final BookIndex index = BookIndex.of(IndexTestBooks.oneUnit("Arlo Venn smiled."));

        final List<GlossaryEntry> withIndex = NameClusters.of(entries, index).membersOf(named(entries, "Arlo"));
        final List<GlossaryEntry> withoutIndex = NameClusters.of(entries, null).membersOf(named(entries, "Arlo"));

        assertThat(withIndex).extracting(GlossaryEntry::term).containsExactly("Arlo", "Arlo Venn");
        assertThat(withoutIndex).isEqualTo(withIndex);
    }

    // The cluster is recomputed from the entries given, so a name added later changes it with no stored field.
    @Test
    void of_secondFullNameAdded_makesTheSurnameAmbiguousAgain() {
        final List<GlossaryEntry> before = entries("Venn=C, Arlo Venn=C");
        final List<GlossaryEntry> after = entries("Venn=C, Arlo Venn=C, Mira Venn=C");

        assertThat(NameClusters.of(before, null).membersOf(named(before, "Venn")))
                .extracting(GlossaryEntry::term)
                .containsExactly("Venn", "Arlo Venn");
        assertThat(NameClusters.of(after, null).membersOf(named(after, "Venn")))
                .extracting(GlossaryEntry::term)
                .containsExactly("Venn");
    }

    // 15h.A6 aliases stay with their entry: they are spellings of the cluster, never members or anchors of their own.
    @Test
    void namesOf_clusterWithAliases_listsEveryMembersSpellings() {
        final List<GlossaryEntry> entries = List.of(
                entry("Venn", TermType.CHARACTER).withAliases(List.of("Venns")),
                entry("Arlo Venn", TermType.CHARACTER).withAliases(List.of("Arlo Vennn")));

        final NameClusters clusters = NameClusters.of(entries, null);

        assertThat(clusters.namesOf(entries.getFirst()))
                .containsExactlyInAnyOrder("Venn", "Venns", "Arlo Venn", "Arlo Vennn");
        assertThat(clusters.membersOf(entries.getFirst())).hasSize(2);
    }

    // IF an alias counted as a second full name, THEN a surname held by one person plus a typo of another's name
    // would be ambiguous.
    @Test
    void membersOf_aliasLooksLikeAnotherFullName_isNotASecondAnchor() {
        final List<GlossaryEntry> entries = List.of(
                entry("Venn", TermType.CHARACTER),
                entry("Arlo Venn", TermType.CHARACTER).withAliases(List.of("Mira Venn")));

        assertThat(NameClusters.of(entries, null).membersOf(entries.getFirst()))
                .extracting(GlossaryEntry::term)
                .containsExactly("Venn", "Arlo Venn");
    }

    @Test
    void namesOf_entryAlone_isItsOwnSpellings() {
        final List<GlossaryEntry> entries =
                List.of(entry("Venn", TermType.CHARACTER).withAliases(List.of("Venns")));

        assertThat(NameClusters.of(entries, null).namesOf(entries.getFirst())).containsExactly("Venn", "Venns");
    }

    private static List<GlossaryEntry> entries(final String glossary) {
        return Arrays.stream(glossary.split(","))
                .map(String::strip)
                .map(NameClustersTest::entryOf)
                .toList();
    }

    private static GlossaryEntry entryOf(final String written) {
        final int cut = written.lastIndexOf('=');
        final String kind = written.substring(cut + 1);
        return entry(written.substring(0, cut), typeOf(kind.charAt(0)), genderOf(kind));
    }

    private static Gender genderOf(final String kind) {
        if (kind.length() < 2) {
            return Gender.UNKNOWN;
        }
        return kind.charAt(1) == 'm' ? Gender.MALE : Gender.FEMALE;
    }

    private static TermType typeOf(final char letter) {
        return switch (letter) {
            case 'C' -> TermType.CHARACTER;
            case 'P' -> TermType.PLACE;
            case 'T' -> TermType.TERM;
            default -> TermType.OTHER;
        };
    }

    private static GlossaryEntry entry(final String term, final TermType type) {
        return entry(term, type, Gender.UNKNOWN);
    }

    private static GlossaryEntry entry(final String term, final TermType type, final Gender gender) {
        return new GlossaryEntry("p1:" + term, PROJECT, term, null, type, gender, false);
    }

    private static GlossaryEntry named(final List<GlossaryEntry> entries, final String term) {
        return entries.stream()
                .filter(entry -> entry.term().equals(term))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> terms(final String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::strip).toList();
    }
}
