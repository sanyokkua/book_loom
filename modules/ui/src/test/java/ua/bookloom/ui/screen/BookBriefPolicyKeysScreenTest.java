package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.QuoteStyle;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The Translation policies card's rows the target language's file sets (the apostrophe, the quotation marks, the
 * default address and each declared convention), read from the bundled language files, and the {@code (likely)} mark
 * beside every field the analysis filled.
 */
class BookBriefPolicyKeysScreenTest extends BookBriefScreenTestBase {

    private static final List<String> KEY_ROWS = List.of(
            "brief-policy-apostrophe",
            "brief-policy-quotes",
            "brief-policy-address-strangers",
            "brief-policy-address-family",
            "brief-policy-convention-hardG");

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .prompt(prompt)
                .languages(LanguageRules.bundled())
                .build();
    }

    private void openWithTarget(final String target) throws TimeoutException {
        openFrankensteinThenShowBrief();
        onFx(() -> briefModel().setTargetLanguage(target));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private List<String> segmentTexts(final String id) {
        return ThemeTestSupport.onFx(() ->
                segmented(id).getButtons().stream().map(ToggleButton::getText).toList());
    }

    private void press(final String id, final int segment) {
        onFx(() -> segmented(id).getButtons().get(segment).fire());
    }

    // IF the Ukrainian rows were missing, THEN the model would drift between apostrophes, quotes and г/ґ page by page.
    @Test
    void briefPolicies_ukrainianTarget_showsHardGAndApostropheRows() throws TimeoutException {
        openWithTarget("uk");

        assertThat(List.copyOf(KEY_ROWS))
                .allSatisfy(id -> assertThat(isShown(id)).as(id).isTrue());
        assertThat(segmentTexts("brief-policy-apostrophe")).containsExactly("’ U+2019", "ʼ U+02BC", "' U+0027");
        assertThat(selectedStates("brief-policy-apostrophe")).containsExactly(true, false, false);
        assertThat(segmentTexts("brief-policy-quotes")).containsExactly("«...» „...“", "„...“ ‚...‘");
        assertThat(selectedStates("brief-policy-quotes")).containsExactly(true, false);
        assertThat(segmentTexts("brief-policy-address-strangers")).containsExactly("Ви", "ти");
        assertThat(selectedStates("brief-policy-address-strangers")).containsExactly(true, false);
        assertThat(selectedStates("brief-policy-address-family")).containsExactly(false, true);
        assertThat(segmentTexts("brief-policy-convention-hardG")).containsExactly("г", "ґ");
        assertThat(selectedStates("brief-policy-convention-hardG")).containsExactly(true, false);
        assertThat(((Label) required("brief-policy-convention-hardG-label")).getText())
                .isEqualTo("Hard g in foreign names");
    }

    // IF an English target showed rows its file does not set, THEN the person would choose what nothing reads.
    @ParameterizedTest
    @ValueSource(strings = {"en", "de"})
    void briefPolicies_englishTarget_showsNoExtraRows(final String target) throws TimeoutException {
        openWithTarget("uk");

        onFx(() -> briefModel().setTargetLanguage(target));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(List.copyOf(KEY_ROWS))
                .allSatisfy(id -> assertThat(isShown(id)).as(id).isFalse());
    }

    // IF a pick were not stored, THEN the run would follow the file's default whatever the person chose.
    @Test
    void briefPolicies_eachRowPicked_storesItsKey() throws TimeoutException {
        openWithTarget("uk");

        press("brief-policy-apostrophe", 2);
        press("brief-policy-quotes", 1);
        press("brief-policy-address-family", 0);
        press("brief-policy-convention-hardG", 1);

        assertThat(brief().apostrophe()).isEqualTo("'");
        assertThat(brief().quoteStyle()).isEqualTo(new QuoteStyle("„", "“", "‚", "‘"));
        assertThat(brief().defaultAddress()).isEqualTo(new Address(AddressForm.FORMAL, AddressForm.FORMAL));
        assertThat(brief().conventions()).isEqualTo(Map.of("hardG", "ґ"));
        assertThat(selectedStates("brief-policy-convention-hardG")).containsExactly(false, true);
    }

    // IF a choice explained nothing on hover, THEN a person could not tell ’ from ʼ.
    @Test
    void briefPolicies_ukrainianTarget_everySegmentExplainsItself() throws TimeoutException {
        openWithTarget("uk");

        assertThat(ThemeTestSupport.onFx(() -> TooltipProbe.untipped(required("brief-policies-card"))))
                .isEmpty();
        assertThat(TooltipProbe.tipText(
                        segmented("brief-policy-apostrophe").getButtons().get(1)))
                .contains("U+02BC");
    }

    // IF the Ukrainian rows were cut or squeezed, THEN a long Ukrainian label would end in an ellipsis.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1024, 700", "1280, 800", "1440, 900", "1920, 700"})
    void briefPolicies_ukrainianInterface_fitsWhole(final double width, final double height) throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openWithTarget("uk");

        showAtSceneSize(width, height);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(((Label) required("brief-policy-convention-hardG-label")).getText())
                .isEqualTo("Тверде г в іншомовних іменах");
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(required("brief-policies-card"))))
                .isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.squeezed(required("brief-policies-card"))))
                .isEmpty();
    }

    private void answerFill(final BriefSuggestion suggestion) {
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b"));
        ((ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class)).answersBrief(Result.ok(suggestion));
    }

    private static BriefSuggestion likelyRegister() {
        return new BriefSuggestion(
                null,
                Register.CASUAL,
                null,
                null,
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                Map.of(BriefField.REGISTER, new FieldEvidence(FieldEvidence.Basis.LIKELY, List.of(), 0, 2)));
    }

    // IF a guess looked like a decision, THEN the person would trust it without looking.
    @Test
    void brief_analysisFilledField_showsLikelyUntilEdited() throws TimeoutException {
        openWithTarget("uk");
        answerFill(likelyRegister());

        fillInAndApply();
        awaitFx(() -> isShown("brief-likely-register"));

        assertThat(((Label) required("brief-likely-register")).getText()).isEqualTo("(likely)");
        assertThat(TooltipProbe.tipText(required("brief-likely-register"))).contains("confirm");
        assertThat(isShown("brief-likely-genre")).isFalse();

        onFx(() -> briefModel().setRegister(Register.NEUTRAL));

        assertThat(isShown("brief-likely-register")).isFalse();
    }

    // IF a "(ймовірно)" mark pushed a Ukrainian label or choice out of its card, THEN the mark would hide what it
    // marks.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"944, 600", "1024, 700", "1280, 800", "1440, 900", "1920, 700"})
    void brief_ukrainianLikelyMarks_fitWhole(final double width, final double height) throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openWithTarget("uk");
        onFx(() -> briefModel().setNarratorPerson(NarratorPerson.FIRST));
        answerFill(new BriefSuggestion(
                null,
                Register.CASUAL,
                null,
                null,
                NarratorPerson.FIRST,
                Gender.FEMALE,
                Map.of(
                        BriefField.REGISTER,
                        new FieldEvidence(FieldEvidence.Basis.LIKELY, List.of(), 0, 2),
                        BriefField.NARRATOR_GENDER,
                        new FieldEvidence(FieldEvidence.Basis.LIKELY, List.of(), 0, 2))));
        fillInAndApply();
        awaitFx(() -> isShown("brief-likely-register"));

        showAtSceneSize(width, height);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(((Label) required("brief-tone-narrator-gender-origin")).getText())
                .isEqualTo("Жіноча (ймовірно)");
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(required("brief-tone-card"))))
                .isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.squeezed(required("brief-tone-card"))))
                .isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.overflowing(required("brief-tone-card"), width)))
                .isEmpty();
    }

    // IF a likely narrator's gender read like the book's own, THEN nobody would know to check it.
    @ParameterizedTest
    @CsvSource({
        "LIKELY, MALE, Male (likely)",
        "LIKELY, FEMALE, Female (likely)",
        "PASSAGES, MALE, 'Male, from the book'"
    })
    void brief_narratorGender_namesWhereItCameFrom(
            final FieldEvidence.Basis basis, final Gender gender, final String shown) throws TimeoutException {
        openWithTarget("uk");
        onFx(() -> briefModel().setNarratorPerson(NarratorPerson.FIRST));
        answerFill(new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.FIRST,
                gender,
                Map.of(BriefField.NARRATOR_GENDER, new FieldEvidence(basis, List.of("Hold on tight, Ray"), 1, 2))));

        fillInAndApply();
        awaitFx(() -> isShown("brief-tone-narrator-gender-origin"));

        assertThat(((Label) required("brief-tone-narrator-gender-origin")).getText())
                .isEqualTo(shown);
        assertThat(TooltipProbe.tipText(required("brief-tone-narrator-gender-origin")))
                .isNotBlank();

        onFx(() -> briefModel().setNarratorGender(gender == Gender.MALE ? Gender.FEMALE : Gender.MALE));

        assertThat(isShown("brief-tone-narrator-gender-origin")).isFalse();
    }
}
