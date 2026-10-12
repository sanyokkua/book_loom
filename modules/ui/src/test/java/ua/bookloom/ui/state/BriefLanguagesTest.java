package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** When a brief's languages let the work go on: a target is chosen and differs from the source, which may be Auto. */
class BriefLanguagesTest {

    // IF Auto (no source) blocked Continue, THEN a book declaring no language could not be translated without a guess.
    @ParameterizedTest(name = "source={0} target={1} -> {2}")
    @CsvSource(
            value = {
                "en,uk,true",
                "NULL,uk,true",
                "NULL,en,true",
                "en,NULL,false",
                "NULL,NULL,false",
                "en,en,false",
                "uk,uk,false"
            },
            nullValues = "NULL")
    void isUsable_sourceAndTarget_needsATargetThatDiffersFromAnyChosenSource(
            final String source, final String target, final boolean expected) {
        assertThat(BriefLanguages.isUsable(source, target)).isEqualTo(expected);
    }
}
