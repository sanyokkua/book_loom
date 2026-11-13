package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;

/** A kind the person picks in the table is the person's, whatever set the kind before (design D3). */
class GlossaryEditsTest {

    @ParameterizedTest
    @EnumSource(
            value = Origin.class,
            names = {"BOOK", "RULE", "LIKELY"})
    void type_personPicksAKind_makesItThePersons(final Origin held) {
        final GlossaryEntry entry = GlossaryEntry.of(
                        "p1:wren", "p1", "Wren", null, TermType.OTHER, Gender.UNKNOWN, false)
                .withType(TermType.CHARACTER, held);

        final GlossaryEntry edited = GlossaryEdits.type(entry, TermType.PLACE);

        assertThat(edited.type()).isEqualTo(TermType.PLACE);
        assertThat(edited.typeOrigin()).isEqualTo(Origin.USER);
    }
}
