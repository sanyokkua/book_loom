package ua.bookloom.pipeline.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.TermType;

/** One glossary name spelled two ways across the decided targets is reported once, on its first odd spelling. */
class NameVariantsTest {

    private static GlossaryEntry name(final String term, final String target, final TermType type) {
        return new GlossaryEntry("g-" + term, "p", term, target, type, Gender.UNKNOWN, false);
    }

    private static Map<String, String> targets(final String... segmentAndText) {
        final Map<String, String> targets = new LinkedHashMap<>();
        for (int i = 0; i < segmentAndText.length; i += 2) {
            targets.put(segmentAndText[i], segmentAndText[i + 1]);
        }
        return targets;
    }

    @Test
    void find_nameSpelledWithAVoicedFinal_isReportedOnceOnTheFirstOddSpelling() {
        final Map<String, List<QaFinding>> found = NameVariants.find(
                List.of(name("Miles", "Майлз", TermType.CHARACTER)),
                targets("s1", "Майлз пішов.", "s2", "Майлс сказав.", "s3", "Майлз і Майлс."),
                "uk");

        assertThat(found).containsOnlyKeys("s2");
        assertThat(found.get("s2")).singleElement().satisfies(finding -> {
            assertThat(finding.raisedBy()).isEqualTo("name-variants");
            assertThat(finding.note()).contains("\"Miles\"", "\"Майлз\"", "\"Майлс\"", "2");
        });
    }

    @Test
    void find_nameSpelledWithADoubledLetter_isReported() {
        final Map<String, List<QaFinding>> found = NameVariants.find(
                List.of(name("Bobby", "Боббі", TermType.CHARACTER)),
                targets("s1", "Боббі пішов.", "s2", "Він сказав Бобі це."),
                "uk");

        assertThat(found).containsOnlyKeys("s2");
    }

    @Test
    void find_declinedFormsAndOtherNames_arePlainForms() {
        final Map<String, List<QaFinding>> found = NameVariants.find(
                List.of(name("Miles", "Майлз", TermType.CHARACTER)),
                targets("s1", "Майлз пішов.", "s2", "Він сказав Майлзу це.", "s3", "Майла там не було, а Майло був."),
                "uk");

        assertThat(found).isEmpty();
    }

    @Test
    void find_entryThatIsNotAPersonOrPlaceOrHasNoTarget_isNotChecked() {
        final Map<String, List<QaFinding>> found = NameVariants.find(
                List.of(name("Miles", "Майлз", TermType.TERM), name("Jack", "", TermType.CHARACTER)),
                targets("s1", "Майлз і Майлс. Джек і Джик."),
                "uk");

        assertThat(found).isEmpty();
    }

    @Test
    void find_languageWithoutVoicingData_foldsOnlyDoubledLetters() {
        final Map<String, List<QaFinding>> found = NameVariants.find(
                List.of(name("Miles", "Майлз", TermType.CHARACTER)), targets("s1", "Майлс сказав."), "xx");

        assertThat(found).isEmpty();
    }

    // IF a segment that never names the term counted, THEN another name that folds into it would be a "variant".
    @Test
    void find_segmentWhoseSourceDoesNotNameTheTerm_isNotCounted() {
        final Map<String, List<QaFinding>> found = NameVariants.find(
                List.of(name("Miles", "Майлз", TermType.CHARACTER)),
                targets("s1", "Майлз пішов.", "s2", "Майлс сказав."),
                Map.of("s1", "Miles left.", "s2", "Mike said."),
                "uk");

        assertThat(found).isEmpty();
    }

    @Test
    void find_segmentWhoseSourceNamesTheTerm_isCounted() {
        final Map<String, List<QaFinding>> found = NameVariants.find(
                List.of(name("Miles", "Майлз", TermType.CHARACTER)),
                targets("s1", "Майлз пішов.", "s2", "Майлс сказав."),
                Map.of("s1", "Miles left.", "s2", "Miles said."),
                "uk");

        assertThat(found).containsOnlyKeys("s2");
    }
}
