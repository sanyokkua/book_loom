package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The normaliser works on a segment's masked text, so {@code ⟦gN⟧} tokens stand for markup, code spans and protected
 * names; it must treat them as opaque. A footnote marker is such a token too, and the text cannot tell it from a tag,
 * so spacing before footnote markers is not attempted here.
 */
class TypographyNormalizerTest {

    private static String uk(final String text) {
        return TypographyNormalizer.normalise(text, "uk").text();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "apostrophe in a word|Не пам'ятаю, п'ять і м'яч.|Не пам’ятаю, п’ять і м’яч.",
                "apostrophe in a name|Д'Артаньян прийшов.|Д’Артаньян прийшов.",
                "ellipsis of three dots|Він зупинився... і мовчав.|Він зупинився… і мовчав.",
                "four dots keep a full stop|Кінець....|Кінець….",
                "space before a comma|Так , сказав він.|Так, сказав він.",
                "space before a full stop and a question mark|Що це ? Так .|Що це? Так.",
                "space before an ellipsis typed with dots|Зачекай ... добре.|Зачекай… добре.",
            })
    void normalise_ukrainianText_fixesApostropheEllipsisAndSpacing(
            final String name, final String raw, final String expected) {
        assertThat(uk(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "straight pair|Він сказав: \"Іди геть\".|Він сказав: «Іди геть».",
                "pair with a closing mark inside|\"Іди геть!\" — крикнув він.|«Іди геть!» — крикнув він.",
                "nested pair|\"Він сказав \"так\" і пішов\".|«Він сказав „так“ і пішов».",
                "after a dash|— \"Ні\", — відповів він.|— «Ні», — відповів він.",
            })
    void normalise_straightQuotesInUkrainian_becomeGuillemetsWithNestedLowQuotes(
            final String name, final String raw, final String expected) {
        assertThat(uk(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "«підлий демоне»»",
                "«Він сказав „так“ і пішов».",
                "— Привіт, — сказав він. — Як справи?",
                "— …",
                "Довжиною 5\" і більше.",
                "Звичайний текст без нічого.",
            })
    void normalise_alreadyCorrectOrStrayMarks_isLeftAsItIs(final String text) {
        assertThat(uk(text)).isEqualTo(text);
    }

    @Test
    void normalise_strayClosingGuillemet_staysAndTheOpenQuoteAfterItStillPairs() {
        assertThat(uk("«підлий демоне»» і \"ще\" раз")).isEqualTo("«підлий демоне»» і «ще» раз");
    }

    @Test
    void normalise_dialogueWithDashes_staysDashDialogue() {
        assertThat(uk("— Ти йдеш? — спитав він...")).isEqualTo("— Ти йдеш? — спитав він…");
    }

    @Test
    void normalise_textAroundTokens_neverChangesAToken() {
        final String raw = "Він ⟦g1⟧сказав...⟦g2⟧ \"так\" ⟦g3⟧ , ⟦g4⟧.";

        final String text = uk(raw);

        assertThat(text).contains("⟦g1⟧", "⟦g2⟧", "⟦g3⟧", "⟦g4⟧").isEqualTo("Він ⟦g1⟧сказав…⟦g2⟧ «так» ⟦g3⟧ , ⟦g4⟧.");
    }

    @Test
    void normalise_charactersNextToAToken_areNotDeleted() {
        // the space between a word and a token may carry a tag's whitespace, so it is never removed
        assertThat(uk("слово ⟦g1⟧. і ⟦g2⟧ ,")).isEqualTo("слово ⟦g1⟧. і ⟦g2⟧ ,");
    }

    @Test
    void normalise_apostropheBesideAToken_isLeftAlone() {
        assertThat(uk("п'⟦g1⟧ятниця ⟦g2⟧'ятниця")).isEqualTo("п'⟦g1⟧ятниця ⟦g2⟧'ятниця");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "Не пам'ятаю... \"так\" , сказав він.",
                "\"Він сказав \"так\" і пішов\"...",
                "«підлий демоне»» \"ще",
                "п'ять ⟦g1⟧ \"а\" ⟦g2⟧ ...",
                ". . . і ще . . .",
            })
    void normalise_secondPass_changesNothing(final String raw) {
        final String once = uk(raw);

        final Normalisation twice = TypographyNormalizer.normalise(once, "uk");

        assertThat(twice.text()).isEqualTo(once);
        assertThat(twice.isChanged()).isFalse();
    }

    @Test
    void normalise_unknownLanguage_fixesOnlyApostropheEllipsisAndSpacing() {
        final Normalisation result = TypographyNormalizer.normalise("It's \"fine\" ... really .", "xx");

        assertThat(result.text()).isEqualTo("It’s \"fine\"… really.");
    }

    @Test
    void normalise_french_keepsTheSpaceBeforeAQuestionMark() {
        assertThat(TypographyNormalizer.normalise("Vraiment ? C'est vrai ...", "fr")
                        .text())
                .isEqualTo("Vraiment ? C’est vrai…");
    }

    @Test
    void normalise_english_usesCurlyDoubleQuotes() {
        assertThat(TypographyNormalizer.normalise("He said \"go\".", "en").text())
                .isEqualTo("He said “go”.");
    }

    @Test
    void normalise_changedText_countsEachKindAndNamesThemInTheNote() {
        final Normalisation result = TypographyNormalizer.normalise("Не пам'ятаю ... \"так\" , п'ять", "uk");

        assertThat(result.isChanged()).isTrue();
        assertThat(result.note())
                .isEqualTo("Typography normalised: 2 apostrophes, 1 ellipsis, 2 quote marks, 2 spaces.");
    }

    @Test
    void normalise_cleanText_isNotChangedAndHasNoNote() {
        final Normalisation result = TypographyNormalizer.normalise("Усе гаразд.", "uk");

        assertThat(result.isChanged()).isFalse();
        assertThat(result.text()).isEqualTo("Усе гаразд.");
    }
}
