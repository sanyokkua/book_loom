package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Function-word residue is its own finding, never the wrong-language-paragraph one. */
class LanguageIdentityCheckTest {

    private static List<CheckFinding> ruToUk(final String target) {
        return LanguageIdentityCheck.find(target, "ru", "uk");
    }

    @Test
    void check_ruToUkThreeResidueWords_namesThem() {
        final List<CheckFinding> found = ruToUk("Він сказав, что це правда, это ясно, і когда прийде.");

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.FUNCTION_WORD_RESIDUE);
            assertThat(finding.blocking()).isFalse();
            assertThat(finding.span().text()).isEqualTo("что");
            assertThat(finding.explanation()).contains("что, это, когда");
        });
    }

    @Test
    void check_ukTargetWithHomographs_noFinding() {
        assertThat(ruToUk("Це було просто, тобі казали тебе чекати через дощ.")).isEmpty();
        assertThat(ruToUk("Він просто дивився на тебе через вікно.")).isEmpty();
    }

    @Test
    void check_residueOnly_isNotTheWrongLanguageFinding() {
        final List<CheckFinding> found = ruToUk("Вона не знала, что робити далі.");

        assertThat(found).extracting(CheckFinding::kind).containsExactly(FindingKind.FUNCTION_WORD_RESIDUE);
    }

    // IF the paragraph's share of function words read a modifier-letter apostrophe as a letter, THEN «donʼt» would be
    // a residue word in one count and no function word in the other.
    @Test
    void check_englishParagraphWithModifierApostrophes_isTheWrongLanguageFinding() {
        final List<CheckFinding> found = LanguageIdentityCheck.find(
                "I donʼt know, she didnʼt say, we wasnʼt sure, they couldnʼt go home today.", "en", "pl");

        assertThat(found).extracting(CheckFinding::kind).containsExactly(FindingKind.LEFTOVER_LANGUAGE);
    }

    @Test
    void check_capitalisedResidueWord_isNotCounted() {
        assertThat(ruToUk("Что ж, вона пішла.")).isEmpty();
    }

    @Test
    void check_oneLetterWordBeforeLowerCaseWord_counts() {
        assertThat(ruToUk("Ніч була холодна и тиха."))
                .singleElement()
                .satisfies(finding -> assertThat(finding.span().text()).isEqualTo("и"));
    }

    @Test
    void check_oneLetterWordBeforeUpperCaseWord_isNotCounted() {
        assertThat(ruToUk("Ніч була холодна и Марко мовчав.")).isEmpty();
    }

    @Test
    void check_wholeRussianParagraph_isTheWrongLanguageFindingAndNoResidue() {
        final String line = "Он сказал, что это было очень давно, когда они жили здесь и никто не знал об этом.";

        assertThat(ruToUk(line)).extracting(CheckFinding::kind).containsExactly(FindingKind.LEFTOVER_LANGUAGE);
    }

    @Test
    void check_languageWithoutAFunctionWordFile_noFinding() {
        assertThat(LanguageIdentityCheck.find("Він сказав, что це правда.", "xx", "uk"))
                .isEmpty();
        assertThat(LanguageIdentityCheck.find("Він сказав, что це правда.", null, "uk"))
                .isEmpty();
    }
}
