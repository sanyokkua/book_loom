package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The findings the settings screen draws for a check: the chips, the visible reason under a failed or qualified one,
 * and the in-place refusal of a whole check.
 */
class SettingsChipsScreenTest extends SettingsScreenTestBase {

    // IF a status shared a look with another, or leaned on colour alone, THEN a person could not tell a failed stage
    // from a passed one; each carries its own class, glyph and words.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "PASSED    | chip-ok      | ✓ Connection: Passed",
                "SOFT_PASS | chip-warn    | ! Connection: Passed with a note",
                "FAILED    | chip-err     | ✕ Connection: Failed",
                "SKIPPED   | chip-neutral | – Connection: Not attempted",
            })
    void chips_fourStatuses_renderDistinctly(final StageStatus status, final String styleClass, final String text)
            throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(status));

        final Labeled connection = chip("CONNECTION");
        assertThat(connection.getStyleClass()).contains("chip", styleClass);
        assertThat(connection.getStyleClass())
                .filteredOn(name -> name.startsWith("chip-"))
                .containsExactly(styleClass);
        assertThat(connection.getText()).isEqualTo(text);
    }

    // IF the stages after a failed first one were drawn as passed or left out, THEN the report would misstate what was
    // attempted.
    @Test
    void chips_unreachableFirstStage_threeChipsInOrderLaterOnesNotAttempted() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        assertThat(chips().getChildren())
                .extracting(Node::getId)
                .containsExactly("chip-row-CONNECTION", "chip-row-MODELS", "chip-row-INFERENCE");
        assertThat(chips().getChildren())
                .extracting(child -> ((Labeled) child.lookup(".chip")).getText())
                .containsExactly("✕ Connection: Failed", "– Models: Not attempted", "– Inference: Not attempted");
    }

    // IF a failed stage's reason lived only in a tooltip, THEN a person who cannot hover would see a red chip and
    // never learn why.
    @Test
    void chipDetail_failedStage_showsTheErrorMessageVisibly() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        final Label detail = detail("CONNECTION");
        assertThat(detail.getText()).isEqualTo("The server did not answer.");
        assertThat(detail.getStyleClass()).contains("hint");
        assertThat(detail.isVisible()).isTrue();
        assertThat(chip("CONNECTION").getParent()).isSameAs(required("chip-row-CONNECTION"));
    }

    // IF a soft pass showed the discovery error's message instead of the note, THEN the real verifier's report, which
    // carries both, would read as a failure under a passing chip.
    @Test
    void chipDetail_softPassWithErrorAndNote_showsTheNote() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.SOFT_PASS));

        assertThat(detail("CONNECTION").getText()).isEqualTo("model list unavailable");
    }

    // IF a passed or skipped stage carried a detail line, THEN a person would look for a problem that is not there.
    @Test
    void chipDetail_passedAndSkippedStages_haveNoDetailLine() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.PASSED));

        assertThat(chips().lookupAll(".hint")).isEmpty();
    }

    // IF a refusal the person can fix were not visible in place, THEN pressing Check would appear to do nothing.
    @Test
    void checkRefusal_validationRefusal_shownInPlaceAndHiddenAfterwards() throws TimeoutException {
        openSettings();
        final Label refusal = (Label) required("settings-check-refusal");
        assertThat(refusal.isVisible()).isFalse();
        assertThat(refusal.isManaged()).isFalse();
        verifier.respondWith(Result.err(AppError.of(ErrorCode.validation, "Unknown provider", "No such provider.")));

        onFx(() -> {
            viewModel().model().set(MODEL);
            viewModel().check();
        });
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(refusal::isVisible));

        assertThat(refusal.getText()).isEqualTo("No such provider.");
        assertThat(refusal.isManaged()).isTrue();
        assertThat(refusal.getStyleClass()).contains("hint");
        onFx(() -> viewModel().model().set("another-model"));
        assertThat(refusal.isVisible()).isFalse();
    }

    // IF chips were drawn before any check ran, THEN the screen would report a verdict nobody asked for.
    @Test
    void chips_beforeAnyCheck_noChips() {
        openSettings();

        assertThat(chips().getChildren()).isEmpty();
    }

    // IF a failed stage were not painted with the error background role, THEN it would not match the reference
    // rendering (light block, -color-err-bg).
    @Test
    void chip_failedStage_backgroundIsErrBg() throws TimeoutException {
        openSettings();

        checkWithReport(firstStageOnly(StageStatus.FAILED));

        final Region failed = (Region) chip("CONNECTION");
        assertThat(failed.getBackground()).isNotNull();
        ThemeTestSupport.assertSameColour(
                failed.getBackground().getFills().get(0).getFill(), "#f7e4df", "failed chip background");
    }
}
