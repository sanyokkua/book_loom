package ua.bookloom.ui.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.ShellTestBase;

/**
 * The blocking error dialog as the person meets it: what it says, what it folds away, what it offers, and what it
 * never shows. Every check drives the real presenter over the real modal host in the real shell.
 */
class ErrorPresenterTest extends ShellTestBase {

    private static final String TITLE = "Translation failed";
    private static final String MESSAGE = "The provider gave up after several attempts.";
    private static final String DETAILS = "attempt 3 of 3; model gemma3:12b";
    private static final String CAUSE_TEXT = "Connection refused: localhost/127.0.0.1:11434";

    private static AppError timeoutWithDetails() {
        return AppError.of(ErrorCode.timeout, TITLE, MESSAGE, DETAILS, null);
    }

    private ErrorPresenter presenter() {
        return injector.getInstance(ErrorPresenter.class);
    }

    private void present(final AppError error) {
        onFx(() -> presenter().present(error));
    }

    private void fireToggle() {
        onFx(() -> ((ToggleButton) required("error-details-toggle")).fire());
    }

    private boolean isPresent(final String id) {
        return scene.getRoot().lookup("#" + id) != null;
    }

    private String textOf(final String id) {
        return ((Label) required(id)).getText();
    }

    private static MouseEvent scrimClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                4,
                4,
                4,
                4,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null);
    }

    private String toggleText() {
        return ((ToggleButton) required("error-details-toggle")).getText();
    }

    private String buttonText(final String id) {
        return ((Button) required(id)).getText();
    }

    // IF the card did not carry the failure's own title and message, THEN the person could not tell what failed.
    @Test
    void present_aFailure_showsItsTitleAndMessage() {
        present(timeoutWithDetails());

        assertThat(required("error-card")).isNotNull();
        assertThat(textOf("error-title")).isEqualTo("Translation failed");
        assertThat(textOf("error-message")).isEqualTo("The provider gave up after several attempts.");
    }

    // IF the details were in the scene while folded, THEN "collapsed until expanded" would only be a visual trick.
    @Test
    void present_aFailureWithDetails_keepsDetailsOutOfTheSceneUntilToggled() {
        present(timeoutWithDetails());
        required("error-card");

        assertThat(isPresent("error-details")).isFalse();
        assertThat(textsUnder(scene.getRoot())).noneMatch(text -> text.contains("attempt 3 of 3; model gemma3:12b"));

        fireToggle();

        assertThat(isPresent("error-details")).isTrue();
        assertThat(textsUnder(required("error-details"))).contains("attempt 3 of 3; model gemma3:12b");
    }

    // IF the brief's input rules reached every text area, THEN the details of an error would be repainted with them.
    @Test
    void details_expanded_keepTheirOwnBackgroundAndNotTheBriefsSurfaceFill() {
        present(timeoutWithDetails());
        required("error-card");

        fireToggle();

        final TextArea details = (TextArea) required("error-details");
        assertThat(details.getBackground().getFills().get(0).getFill()).isNotEqualTo(Color.web("#ffffff"));
    }

    // IF the toggle did not fold the details away again, THEN an expanded card could never be tidied.
    @Test
    void toggle_firedTwice_foldsTheDetailsAwayAgain() {
        present(timeoutWithDetails());
        required("error-card");

        fireToggle();
        fireToggle();

        assertThat(isPresent("error-details")).isFalse();
    }

    // IF the toggle kept one label, THEN the person could not tell whether the details were shown or folded.
    @Test
    void toggle_whenExpandedAndFolded_labelsItsNextAction() {
        present(timeoutWithDetails());
        assertThat(toggleText()).isEqualTo("Show details");

        fireToggle();

        assertThat(toggleText()).isEqualTo("Hide details");
    }

    // IF Retry were offered for a failure that is not worth retrying, THEN the person would repeat a doomed action.
    @ParameterizedTest
    @CsvSource({"timeout,true", "validation,false"})
    void present_byRetryability_offersRetryOnlyWhenMarkedRetryable(final ErrorCode code, final boolean retryOffered) {
        onFx(() -> presenter().present(AppError.of(code, TITLE, MESSAGE), () -> {}));
        required("error-card");

        assertThat(isPresent("error-retry")).isEqualTo(retryOffered);
        assertThat(isPresent("error-dismiss")).isTrue();
    }

    // IF a retryable failure presented without a retry action still offered Retry, THEN the button would do nothing.
    @Test
    void present_withoutRetryAction_offersDismissOnly() {
        present(timeoutWithDetails());
        required("error-card");

        assertThat(isPresent("error-retry")).isFalse();
        assertThat(isPresent("error-dismiss")).isTrue();
    }

    // IF Retry did not run the callback exactly once and close the dialog, THEN the action would repeat under a
    // dialog that is still open, or not at all.
    @Test
    void retry_clicked_runsTheCallbackOnceAndHidesTheDialog() {
        final AtomicInteger retries = new AtomicInteger();
        onFx(() -> presenter().present(timeoutWithDetails(), retries::incrementAndGet));
        required("error-card");

        onFx(() -> ((Button) required("error-retry")).fire());

        assertThat(retries.get()).isEqualTo(1);
        assertThat(isPresent("error-card")).isFalse();
        assertThat(required("shell-scrim").isVisible()).isFalse();
    }

    // IF Dismiss ran the retry action, THEN closing a failure would silently repeat it.
    @Test
    void dismiss_clicked_hidesTheDialogAndRunsNoCallback() {
        final AtomicInteger retries = new AtomicInteger();
        onFx(() -> presenter().present(timeoutWithDetails(), retries::incrementAndGet));
        required("error-card");

        onFx(() -> ((Button) required("error-dismiss")).fire());

        assertThat(retries.get()).isZero();
        assertThat(isPresent("error-card")).isFalse();
        assertThat(required("shell-scrim").isVisible()).isFalse();
    }

    // IF a click on the dimmed area closed the card, THEN a failure could be lost without being acknowledged.
    @Test
    void present_aFailure_isNotClosedByTheScrimButByItsOwnButtons() {
        final AtomicInteger retries = new AtomicInteger();
        onFx(() -> presenter().present(timeoutWithDetails(), retries::incrementAndGet));
        required("error-card");

        onFx(() -> required("shell-scrim").fireEvent(scrimClick()));

        assertThat(isPresent("error-card")).isTrue();
        assertThat(retries.get()).isZero();
    }

    // IF the underlying cause were rendered while the details are folded, THEN an unfiltered throwable message
    // could leak to the screen.
    @Test
    void present_aFailureWithACause_showsNoCauseTextWhileFolded() {
        present(AppError.of(ErrorCode.unreachable, TITLE, MESSAGE, DETAILS, new ConnectException(CAUSE_TEXT)));
        required("error-card");

        assertNoCauseText();
    }

    // IF the underlying cause were rendered once the details are expanded, THEN the one unfiltered part of a
    // failure would reach the person exactly when they look closer.
    @Test
    void present_aFailureWithACause_showsNoCauseTextWhenExpanded() {
        present(AppError.of(ErrorCode.unreachable, TITLE, MESSAGE, DETAILS, new ConnectException(CAUSE_TEXT)));
        required("error-card");

        fireToggle();

        assertThat(isPresent("error-details")).isTrue();
        assertNoCauseText();
    }

    private void assertNoCauseText() {
        final List<String> texts = textsUnder(scene.getRoot());
        assertThat(texts).noneMatch(text -> text.contains("Connection refused"));
        assertThat(texts).noneMatch(text -> text.contains("127.0.0.1:11434"));
        assertThat(texts).noneMatch(text -> text.contains("ConnectException"));
        assertThat(texts).noneMatch(text -> text.contains("\tat "));
    }

    // IF a blank or missing details string still built an expander, THEN the person would open an empty section.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void present_withoutUsableDetails_offersNoDetailsToggle(final String details) {
        present(AppError.of(ErrorCode.timeout, TITLE, MESSAGE, details, null));
        required("error-card");

        assertThat(isPresent("error-details-toggle")).isFalse();
        assertThat(isPresent("error-details")).isFalse();
    }

    // IF a second failure stacked on the first, THEN two dialogs would sit over one scrim.
    @Test
    void present_aSecondFailure_replacesTheFirstCard() {
        present(AppError.of(ErrorCode.internal, "First failure", "First message."));
        present(AppError.of(ErrorCode.internal, "Second failure", "Second message."));

        assertThat(scene.getRoot().lookupAll("#error-card")).hasSize(1);
        assertThat(textOf("error-title")).isEqualTo("Second failure");
    }

    // IF the dialog's own words came from anywhere but the catalogue, THEN the Ukrainian display would be half
    // English; the failure's own title stays as the failure gave it.
    @Test
    void present_underUkrainian_drawsItsOwnWordsFromTheUkrainianCatalogue() {
        useLocale(Locale.of("uk"));

        onFx(() -> presenter().present(timeoutWithDetails(), () -> {}));
        required("error-card");

        assertThat(toggleText()).isEqualTo("Показати подробиці");
        assertThat(buttonText("error-retry")).isEqualTo("Повторити");
        assertThat(buttonText("error-dismiss")).isEqualTo("Закрити");
        assertThat(textOf("error-title")).isEqualTo("Translation failed");
    }
}
