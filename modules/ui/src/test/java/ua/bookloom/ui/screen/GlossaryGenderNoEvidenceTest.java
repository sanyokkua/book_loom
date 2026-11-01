package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The Gender column never reads "unknown": a character with no gender yet reads "not specified", and adds "no evidence in
 * the book" only when the names pass read the book and found none (15i.N5); a character nobody has read, or whose gender
 * the person cleared, says no more than that (glossary spec, "The Gender column").
 */
class GlossaryGenderNoEvidenceTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry NO_EVIDENCE = GlossaryEntry.of(
                    "e1", "p1", "Odo", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withNoGenderEvidence();
    private static final GlossaryEntry NEVER_SCANNED =
            GlossaryEntry.of("e2", "p1", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final GlossaryEntry EDITED_BACK = NO_EVIDENCE.withGender(Gender.UNKNOWN);

    private List<String> genderLabels(final Locale locale) throws Exception {
        useLocale(locale);
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(NO_EVIDENCE, NEVER_SCANNED)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        return ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                .map(label -> ((Label) label).getText())
                .toList());
    }

    @Test
    void table_englishNoEvidenceCharacter_saysNotSpecifiedWithTheReasonOnlyWhenTheBookGaveNone() throws Exception {
        assertThat(genderLabels(Locale.ENGLISH))
                .doesNotContain("unknown")
                .filteredOn(label -> label.startsWith("not specified"))
                .containsExactly("not specified — no evidence in the book", "not specified");
    }

    @Test
    void table_ukrainianNoEvidenceCharacter_saysItInUkrainian() throws Exception {
        assertThat(genderLabels(Locale.forLanguageTag("uk")))
                .doesNotContain("невідомо")
                .filteredOn(label -> label.startsWith("не вказано"))
                .containsExactly("не вказано — у книзі немає свідчень", "не вказано");
    }

    // IF a gender the person cleared read "unknown", THEN the column would show the word the spec rules out; IF it read
    // "no evidence in the book", THEN it would misstate why it is empty.
    @Test
    void table_personClearedTheGender_readsNotSpecified() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(EDITED_BACK)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        final List<String> labels =
                ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                        .map(label -> ((Label) label).getText())
                        .toList());

        assertThat(labels)
                .contains("not specified")
                .doesNotContain("not specified — no evidence in the book", "unknown");
    }
}
