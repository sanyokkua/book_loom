package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;

class StyleSheetTest {

    private static final String DEFAULT_LINE = "Neutral, faithful literary prose: keep the author's register, "
            + "sentence rhythm and paragraph breaks; use the standard modern orthography of the target language.";

    @Test
    void from_defaultBrief_rendersTodaysStyleLine() {
        assertThat(StyleSheet.from(BookBrief.defaults("en")).text()).isEqualTo(DEFAULT_LINE);
    }

    @Test
    void from_detectiveBrief_carriesEntriesAndPhrases() {
        final String text = StyleSheet.from(detective()).text();

        assertThat(text)
                .startsWith(DEFAULT_LINE + "\n")
                .contains("Genre: Detective fiction")
                .contains("Narrative voice / era: Victorian, first person")
                .contains("Audience: Adults")
                .contains("Register: formal and literary; prefer elevated, careful diction.")
                .contains("Names: transliterate personal and place names into the target language's script.")
                .contains("Units: convert measurements given in prose to metric units.");
    }

    @Test
    void from_sameBriefTwice_givesIdenticalTextAndHash() {
        final StyleSheet first = StyleSheet.from(detective());
        final StyleSheet second = StyleSheet.from(detective());

        assertThat(second).isEqualTo(first);
        assertThat(first.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    void from_differentBriefs_giveDifferentHashes() {
        assertThat(StyleSheet.from(detective()).hash())
                .isNotEqualTo(StyleSheet.from(BookBrief.defaults("en")).hash());
    }

    @ParameterizedTest
    @CsvSource({"0,1", "19,1", "20,2", "39,2", "40,3", "55,3", "60,4", "79,4", "80,5", "100,5"})
    void bandOf_balance_selectsBand(final int balance, final int band) {
        assertThat(StylePhrases.bandOf(balance)).isEqualTo(band);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "19,1", "20,2", "40,3", "60,4", "80,5", "100,5"})
    void from_nonDefaultBalance_carriesBandPhrase(final int balance, final int band) {
        final BookBrief brief = withBalance(balance);

        assertThat(StyleSheet.from(brief).text())
                .contains(StylePhrases.bundled().balance(band));
        assertThat(StylePhrases.bundled().balance(band)).startsWith("Balance:");
    }

    @Test
    void from_balance0And100_useDifferentPhrases() {
        assertThat(StyleSheet.from(withBalance(0)).text())
                .contains("stay as close to the source wording")
                .doesNotContain("favor natural");
        assertThat(StyleSheet.from(withBalance(100)).text()).contains("favor natural, idiomatic");
    }

    @ParameterizedTest
    @EnumSource(Register.class)
    void bundled_everyRegister_hasPhrase(final Register value) {
        assertThat(StylePhrases.bundled().phrase(value)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(NamePolicy.class)
    void bundled_everyNamePolicy_hasPhrase(final NamePolicy value) {
        assertThat(StylePhrases.bundled().phrase(value)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(ForeignPassagePolicy.class)
    void bundled_everyForeignPolicy_hasPhrase(final ForeignPassagePolicy value) {
        assertThat(StylePhrases.bundled().phrase(value)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(FootnotePolicy.class)
    void bundled_everyFootnotePolicy_hasPhrase(final FootnotePolicy value) {
        assertThat(StylePhrases.bundled().phrase(value)).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(UnitPolicy.class)
    void bundled_everyUnitPolicy_hasPhrase(final UnitPolicy value) {
        assertThat(StylePhrases.bundled().phrase(value)).isNotBlank();
    }

    @Test
    void load_missingKey_failsNamingIt() {
        final var empty = new ByteArrayInputStream("default.line=x".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> StylePhrases.load(() -> empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("style-phrases.properties")
                .hasMessageContaining("Register.FORMAL_LITERARY");
    }

    @Test
    void foreignPassageRule_keep_namesTheSourceLanguage() {
        assertThat(StyleSheet.foreignPassageRule(ForeignPassagePolicy.KEEP, "Ukrainian (uk)"))
                .isEqualTo("If a passage is deliberately in a language other than Ukrainian (uk), "
                        + "keep it verbatim; do not translate it.");
    }

    private static BookBrief detective() {
        return new BookBrief(
                "en",
                "uk",
                "Detective fiction",
                Register.FORMAL_LITERARY,
                "Victorian, first person",
                "Adults",
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.KEEP,
                FootnotePolicy.TRANSLATE,
                UnitPolicy.METRIC,
                55,
                AlsoTranslate.defaults(),
                QualityDial.BALANCED);
    }

    private static BookBrief withBalance(final int balance) {
        final BookBrief base = BookBrief.defaults("en");
        return new BookBrief(
                base.sourceLanguage(),
                base.targetLanguage(),
                base.genre(),
                base.register(),
                base.voiceEra(),
                base.audience(),
                base.names(),
                base.foreignPassages(),
                base.footnotes(),
                base.units(),
                balance,
                base.alsoTranslate(),
                base.dial());
    }
}
