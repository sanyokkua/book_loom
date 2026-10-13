package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.SuggestFixtures.entry;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;

/** The word-by-word agreement of a person's spellings (15i.N6), below the call that uses it. */
class ClusterAgreementTest {

    private static final GlossaryEntry FULL = entry("Arlo Venn", TermType.CHARACTER, Gender.MALE, "Арло Венн");
    private static final GlossaryEntry SURNAME = entry("Venn", TermType.CHARACTER, Gender.MALE, null);
    private static final GlossaryEntry ADDRESS = entry("Mr Venn", TermType.CHARACTER, Gender.MALE, null);

    private static ClusterAgreement over(final GlossaryEntry... asked) {
        final List<GlossaryEntry> held = List.of(FULL, SURNAME, ADDRESS);
        return ClusterAgreement.over(NameClusters.of(held, null, "en"), held, List.of(asked));
    }

    @Test
    void statementFor_surnameOfAHeldFullName_namesTheWordItTakes() {
        assertThat(over(SURNAME).statementFor(SURNAME)).hasValue("Arlo Venn → Арло Венн, so Venn → Венн");
    }

    @Test
    void statementFor_formOfAddressWithAWordTheFullNameLacks_statesOnlyTheFullName() {
        assertThat(over(ADDRESS).statementFor(ADDRESS))
                .hasValueSatisfying(line ->
                        assertThat(line).isEqualTo("Arlo Venn → Арло Венн").doesNotContain("so"));
    }

    @Test
    void statementFor_fullNameItself_isEmpty() {
        assertThat(over(FULL).statementFor(FULL)).isEmpty();
    }

    @Test
    void align_fullNameRenderedWithAHyphen_keepsTheFullNamesWholeWord() {
        final List<GlossaryEntry> held = List.of(FULL.withTarget("Арло-Венн"), SURNAME);
        final ClusterAgreement agreement =
                ClusterAgreement.over(NameClusters.of(held, null, "en"), held, List.of(SURNAME));

        assertThat(agreement.align(List.of(new Suggestion(SURNAME, "Вен"))))
                .extracting(Suggestion::target)
                .containsExactly("Венн");
    }

    @Test
    void align_renderingWithAnotherNumberOfWordsThanItsName_isLeftAlone() {
        final List<GlossaryEntry> held = List.of(FULL.withTarget("Арло Венн Третій"), SURNAME);
        final ClusterAgreement agreement =
                ClusterAgreement.over(NameClusters.of(held, null, "en"), held, List.of(SURNAME));

        assertThat(agreement.align(List.of(new Suggestion(SURNAME, "Вен"))))
                .extracting(Suggestion::target)
                .containsExactly("Вен");
    }
}
