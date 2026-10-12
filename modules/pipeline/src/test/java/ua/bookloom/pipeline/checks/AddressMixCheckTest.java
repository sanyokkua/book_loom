package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * One quoted speech must address its listener one way: ти-forms and capitalised Ви-forms together are a mix the
 * reader hears as a slip. A capitalised Ви-form counts only in the middle of a sentence, because a sentence start is
 * capitalised whatever the word is; the ти-forms count in any case.
 */
class AddressMixCheckTest {

    private static final String MIXED = "«Сядь, тобі треба відпочити, а я принесу Вам чаю.»";

    private static List<CheckFinding> uk(final String target) {
        return AddressMixCheck.find(target, "uk");
    }

    // IF a speech writes тобі and Вам for one listener, THEN the draft is not kept as it stands (the real-run slip).
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "informal then formal object|«Сядь, тобі треба відпочити, а я принесу Вам чаю.»",
                "the real-run speech, Ти at the start|«Ти втомилася, Нелл. Сядьте, Вам треба відпочити, а я принесу Вам чаю.»",
                "possessives|«Це твій плащ, а це Ваш капелюх.»",
                "two sentences of one speech|«Ти втомилася. Я бачу, що Вас це тривожить.»",
                "dash dialogue|— Тобі треба відпочити, а я принесу Вам чаю, — сказала вона.",
                "formal first, informal last|«Скажіть, Ви мене чуєте? Тебе ніхто не кликав.»",
            })
    void find_oneSpeechWithBothForms_isOneSoftFinding(final String name, final String target) {
        assertThat(uk(target)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.ADDRESS_MIX);
            assertThat(finding.blocking()).isFalse();
        });
    }

    // IF the finding did not name both forms, THEN the directed fix could not tell which way to unify the speech.
    @Test
    void find_mixedSpeech_spansTheCapitalisedFormAndNamesBothForms() {
        final CheckFinding finding = uk(MIXED).getFirst();

        assertThat(finding.span().text()).isEqualTo("Вам");
        assertThat(MIXED.substring(finding.span().start(), finding.span().end()))
                .isEqualTo("Вам");
        assertThat(finding.explanation()).contains("тобі", "Вам");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "all informal|«Ти втомилася, Нелл. Сядь, тобі треба відпочити, а я принесу тобі чаю.»",
                "all formal|«Ви втомилися, Нелл. Сядьте, Вам треба відпочити, а я принесу Вам чаю.»",
                "informal possessives only|«Це твій плащ, а це твоя шапка.»",
                "no address at all|«Сядьмо, вже пізно.»",
            })
    void find_oneSpeechAddressedOneWay_saysNothing(final String name, final String target) {
        assertThat(uk(target)).isEmpty();
    }

    // IF the speeches of one paragraph were pooled, THEN a ти speech to a child and a Ви speech to a stranger would
    // look like one mixed speech.
    @Test
    void find_twoSpeechesOneInformalOneFormal_saysNothing() {
        assertThat(uk("«Сядь, тобі треба відпочити.» Потім вона спитала: «Чи не бажаєте Ви чаю?»"))
                .isEmpty();
    }

    @Test
    void find_twoMixedSpeeches_isOneFindingEach() {
        assertThat(uk("«Сядь, тобі треба відпочити, а я принесу Вам чаю.» Він відповів: «Дякую тобі, а Вам кави.»"))
                .hasSize(2);
    }

    @Test
    void find_oneMixedSpeechAndOneCleanSpeech_isOneFinding() {
        assertThat(uk(MIXED + " Він відповів: «Дякую тобі.»")).hasSize(1);
    }

    // A sentence start is capitalised whatever the word is: a Ви-form there is ambiguous and is not held against the
    // speech (decision pinned by 15i.T6; a sentence-initial form beside a mid-sentence one is already a mix).
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Ви at the start of the speech|«Ви втомилися? Сядь, тобі треба відпочити.»",
                "Вам at the start of a later sentence|«Сядь, тобі треба відпочити. Вам нічого не загрожує.»",
                "Вас at the start after a question mark|«Хто тебе кликав? Вас ніхто не кликав.»",
            })
    void find_formalFormOnlyAtASentenceStart_isNotCountedAsFormal(final String name, final String target) {
        assertThat(uk(target)).isEmpty();
    }

    @Test
    void find_capitalisedFormInTheMiddleOfASentenceAfterAComma_isCountedAsFormal() {
        assertThat(uk("«Ну що, Вам щось потрібно? Сядь, тобі буде зручніше.»")).hasSize(1);
    }

    // A lower-case ви is the plural as often as the polite singular, so only the capitalised Ви-form is a sign.
    @Test
    void find_lowerCaseVyInASpeechWithTyForms_isNotCountedAsFormal() {
        assertThat(uk("«Сядь, тобі треба відпочити, а ви всі йдіть геть.»")).isEmpty();
    }

    // IF the narrator's own words were read, THEN "Вам" in a remark beside a ти speech would flag a good paragraph.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "narration beside an informal speech|Нелл сказала тобі, що Вам дали чай, і додала: «Сядь, тобі тут добре.»",
                "narration with both forms and no speech|Вона принесла тобі чаю, а Вам цукру.",
                "dash remark beside an informal speech|— Сядь, тобі треба відпочити, — сказала вона Вам.",
            })
    void find_formsOutsideTheQuotedSpeech_areIgnored(final String name, final String target) {
        assertThat(uk(target)).isEmpty();
    }

    // IF the check ran for every target, THEN a Russian or Polish text would be held to Ukrainian pronoun lists; the
    // lists live in the target's language file, so a language without them is off.
    @ParameterizedTest
    @ValueSource(strings = {"en", "pl", "de", "ru", "xx"})
    void find_targetWithoutTheLists_isOff(final String targetLanguage) {
        assertThat(AddressMixCheck.find(MIXED, targetLanguage)).isEmpty();
    }

    @Test
    void find_emptyTarget_saysNothing() {
        assertThat(uk("")).isEmpty();
    }
}
