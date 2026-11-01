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
 * The Gender column never reads "unknown": a character with no gender yet — the scan found no evidence (15i.N5), nobody
 * has read it, or the person cleared it — reads "not specified" with the reason (glossary spec, "The Gender column").
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
    void table_englishNoEvidenceCharacter_saysNotSpecifiedWithTheReason() throws Exception {
        assertThat(genderLabels(Locale.ENGLISH))
                .doesNotContain("unknown")
                .filteredOn("not specified — no evidence in the book"::equals)
                .hasSize(2);
    }

    @Test
    void table_ukrainianNoEvidenceCharacter_saysItInUkrainian() throws Exception {
        assertThat(genderLabels(Locale.forLanguageTag("uk")))
                .doesNotContain("невідомо")
                .filteredOn("не вказано — у книзі немає свідчень"::equals)
                .hasSize(2);
    }

    // IF a gender the person cleared read "unknown", THEN the column would show the word the spec rules out.
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

        assertThat(labels).contains("not specified — no evidence in the book").doesNotContain("unknown");
    }
}
