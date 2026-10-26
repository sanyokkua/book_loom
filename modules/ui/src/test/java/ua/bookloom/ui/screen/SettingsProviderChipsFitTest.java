package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The provider form's two chips at the smallest window, in English and Ukrainian: the "set / not set" word beside the
 * key variable and a check's result sentence keep every word (G1-3: "не за…" and "× retest-bearer answered, but
 * refused …" at 960 px).
 */
class SettingsProviderChipsFitTest extends SettingsScreenTestBase {

    private static final String LONG_NAME = "retest-bearer";

    // IF a chip ended its words in an ellipsis, THEN the person would read "не за…" or half a verdict.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 944, 600", "uk, 944, 600", "en, 1920, 700", "uk, 1920, 700"})
    void chips_unsetKeyAndRefusedSignIn_cutNoWord(final String language, final double width, final double height)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        openSettings();
        resizeScene(width, height);
        onFx(() -> form().edit(draft -> draft.withName(LONG_NAME)
                .withSignIn(ProviderAuth.BEARER)
                .withCredentialRef("RETEST_MISSING_KEY")));

        checkWithReport(refusedSignIn());

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));
        assertThat(ThemeTestSupport.onFx(() -> required("settings-form-key-status").isVisible()))
                .isTrue();
        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
    }

    private static Result<VerificationReport> refusedSignIn() {
        final AppError auth = AppError.of(ErrorCode.auth, "Sign-in refused", "The server answered 401.");
        return Result.ok(new VerificationReport(
                List.of(new StageOutcome(VerificationStage.CONNECTION, StageStatus.FAILED, auth, null))));
    }
}
