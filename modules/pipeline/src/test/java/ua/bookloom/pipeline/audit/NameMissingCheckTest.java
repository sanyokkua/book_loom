package ua.bookloom.pipeline.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.TermType;

class NameMissingCheckTest {

    private static GlossaryEntry entry(
            final String term, final String target, final TermType type, final boolean locked) {
        return new GlossaryEntry("g", "p", term, target, type, Gender.UNKNOWN, locked);
    }

    @ParameterizedTest
    @CsvSource({"Нелл повільно відчинила двері.", "Нелла повільно відчинила двері.", "Двері повільно відчинила Нелл."})
    void find_nameKeptOrDeclined_saysNothing(final String target) {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.find(glossary, "Nell opened the door slowly.", target))
                .isEmpty();
    }

    @Test
    void find_nameDroppedFromTheTarget_namesTheTermAndItsTarget() {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        final QaFinding finding = NameMissingCheck.find(
                        glossary, "Nell opened the door slowly.", "Вона відчинила двері.")
                .orElseThrow();

        assertThat(finding.raisedBy()).isEqualTo("name-missing");
        assertThat(finding.note()).contains("\"Nell\" → \"Нелл\"");
    }

    @Test
    void find_possessiveInTheSource_stillCountsAsTheName() {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.find(glossary, "Nell's door was open.", "Двері були відчинені."))
                .isPresent();
    }

    @ParameterizedTest
    @CsvSource({"TERM,false,Нелл", "CHARACTER,true,Нелл", "CHARACTER,false,''"})
    void find_entryThatIsNotAnUnlockedNameWithATarget_isNotChecked(
            final TermType type, final boolean locked, final String target) {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", target, type, locked));

        assertThat(NameMissingCheck.find(glossary, "Nell opened the door.", "Вона відчинила двері."))
                .isEmpty();
    }

    @Test
    void find_nameNotInTheSource_saysNothing() {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.find(glossary, "The door opened slowly.", "Двері повільно відчинилися."))
                .isEmpty();
    }
}
