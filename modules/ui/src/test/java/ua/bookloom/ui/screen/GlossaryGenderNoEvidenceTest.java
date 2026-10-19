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
 * A character the scan found no evidence of a gender for reads "not specified" with the reason, so the person can see
 * the scan looked and found nothing (15i.N5); a character nobody has scanned and a person's own "unknown" read as
 * plain "unknown".
 */
class GlossaryGenderNoEvidenceTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry NO_EVIDENCE = new GlossaryEntry(
                    "e1", "p1", "Odo", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withNoGenderEvidence();
    private static final GlossaryEntry NEVER_SCANNED =
            new GlossaryEntry("e2", "p1", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false);
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
                .contains("not specified — no evidence in the book", "unknown")
                .doesNotContain("unknown (suggested)");
    }

    @Test
    void table_ukrainianNoEvidenceCharacter_saysItInUkrainian() throws Exception {
        assertThat(genderLabels(Locale.forLanguageTag("uk")))
                .contains("не вказано — у книзі немає свідчень", "невідомо");
    }

    // The person's edit back to unknown is not the scan's finding.
    @Test
    void table_personEditedTheMarkedGenderBackToUnknown_readsPlainUnknown() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(EDITED_BACK)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));

        final List<String> labels =
                ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                        .map(label -> ((Label) label).getText())
                        .toList());

        assertThat(labels).contains("unknown").noneMatch(label -> label.contains("no evidence"));
    }
}
