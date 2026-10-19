package ua.bookloom.pipeline.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/** A common word the name folds into is not a spelling of the name (15h.E1; the Oct 9 "Він" for "Вінн" shape). */
class NameVariantsPronounTest {

    // IF the folded pronoun were a variant, THEN every sentence starting with "Він" would be reported as a misspelt
    // Wynn.
    @Test
    void find_pronounThatFoldsToTheNameAndTheTargetsUseInLowerCase_isNotAVariant() {
        final GlossaryEntry wynn =
                new GlossaryEntry("g-Wynn", "p", "Wynn", "Вінн", TermType.CHARACTER, Gender.MALE, false);
        final Map<String, String> targets = new LinkedHashMap<>();
        targets.put("s1", "Вінн пішов додому.");
        targets.put("s2", "Він сказав, що він утомився.");
        targets.put("s3", "Вінн побачив, що він сам.");

        final Map<String, List<ua.bookloom.api.project.QaFinding>> found =
                NameVariants.find(List.of(wynn), targets, "uk");

        assertThat(found).isEmpty();
    }
}
