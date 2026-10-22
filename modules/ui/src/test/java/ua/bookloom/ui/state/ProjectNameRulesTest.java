package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Project;

/** What a typed project name is judged against: the other projects of the same book, ignoring case and spaces. */
class ProjectNameRulesTest {

    private static final ProjectNameRules RULES = new ProjectNameRules(
            Set.of(Project.keyOf("Земне тяжіння"), Project.keyOf("Earth Gravity · 26b")), null, true);

    // IF the check were case-sensitive, THEN "ЗЕМНЕ ТЯЖІННЯ" would be stored beside "Земне тяжіння" in one book.
    @ParameterizedTest
    @CsvSource({
        "Земне тяжіння, USED",
        "ЗЕМНЕ ТЯЖІННЯ, USED",
        "'  земне тяжіння  ', USED",
        "earth gravity · 26B, USED",
        "Tide Tables · 26b, OK",
        "Земне, OK"
    })
    void check_typedName_isUsedOnlyWhenAnotherProjectOfTheBookHasItsFoldedKey(
            final String typed, final ProjectNameRules.Check expected) {
        assertThat(RULES.check(typed)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "'   '"})
    void check_blankName_isEmpty(final String typed) {
        assertThat(RULES.check(typed)).isEqualTo(ProjectNameRules.Check.EMPTY);
    }

    @Test
    void check_noOtherProjectOfTheBook_acceptsAnyNonBlankName() {
        final ProjectNameRules alone = new ProjectNameRules(Set.of(), "Earth Gravity · EN → UK", false);

        assertThat(alone.check("Земне тяжіння")).isEqualTo(ProjectNameRules.Check.OK);
        assertThat(alone.automaticName()).isEqualTo("Earth Gravity · EN → UK");
        assertThat(alone.pairFree()).isFalse();
    }
}
