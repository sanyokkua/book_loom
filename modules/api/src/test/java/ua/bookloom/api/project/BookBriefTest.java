package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * {@code BookBrief}'s defaults and balance validation.
 */
class BookBriefTest {

    @Test
    void defaults_noArguments_matchesTheBriefDefaults() {
        final BookBrief brief = BookBrief.defaults("en");

        assertThat(brief.sourceLanguage()).isEqualTo("en");
        assertThat(brief.targetLanguage()).isNull();
        assertThat(brief.genre()).isNull();
        assertThat(brief.register()).isEqualTo(Register.NEUTRAL);
        assertThat(brief.voiceEra()).isNull();
        assertThat(brief.audience()).isNull();
        assertThat(brief.names()).isEqualTo(NamePolicy.TRANSLITERATE);
        assertThat(brief.foreignPassages()).isEqualTo(ForeignPassagePolicy.KEEP);
        assertThat(brief.footnotes()).isEqualTo(FootnotePolicy.TRANSLATE);
        assertThat(brief.units()).isEqualTo(UnitPolicy.KEEP);
        assertThat(brief.balance()).isEqualTo(55);
        assertThat(brief.dial()).isEqualTo(QualityDial.BALANCED);
        assertThat(brief.alsoTranslate()).isEqualTo(new AlsoTranslate(true, true, true, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void constructor_balanceOutsideRange_throws(final int balance) {
        assertThatThrownBy(() -> new BookBrief(
                        "en",
                        null,
                        null,
                        Register.NEUTRAL,
                        null,
                        null,
                        NamePolicy.TRANSLITERATE,
                        ForeignPassagePolicy.KEEP,
                        FootnotePolicy.TRANSLATE,
                        UnitPolicy.KEEP,
                        balance,
                        AlsoTranslate.defaults(),
                        QualityDial.BALANCED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void constructor_balanceAtBounds_isAccepted(final int balance) {
        final BookBrief brief = new BookBrief(
                "en",
                null,
                null,
                Register.NEUTRAL,
                null,
                null,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.KEEP,
                FootnotePolicy.TRANSLATE,
                UnitPolicy.KEEP,
                balance,
                AlsoTranslate.defaults(),
                QualityDial.BALANCED);

        assertThat(brief.balance()).isEqualTo(balance);
    }
}
