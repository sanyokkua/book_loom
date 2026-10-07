package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/** A gender that only the first-name list suggested is marked in the table, and a confirmed one is not. */
class GlossaryGenderSuggestedTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry SUGGESTED = new GlossaryEntry(
                    "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.MALE);
    private static final GlossaryEntry CONFIRMED =
            new GlossaryEntry("e2", "p1", "Hermione", null, TermType.CHARACTER, Gender.FEMALE, false);

    private List<String> genderLabels() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(SUGGESTED, CONFIRMED)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        return ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                .map(label -> ((Label) label).getText())
                .toList());
    }

    @Test
    void table_suggestedAndConfirmedGenders_markOnlyTheSuggestedOne() throws Exception {
        assertThat(genderLabels()).contains("male (suggested)", "female").doesNotContain("female (suggested)");
    }
}
