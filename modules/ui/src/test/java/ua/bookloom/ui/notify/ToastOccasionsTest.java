package ua.bookloom.ui.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The closed list of occasions the notifications spec names: nothing else can raise a toast, and each occasion raises
 * its own message with its own severity, through the real stack.
 */
class ToastOccasionsTest extends ToastsTestBase {

    // IF a tenth occasion crept in, THEN a message with no named caller would be raised from wherever a screen likes.
    @Test
    void occasions_listed_areExactlyTheNineTheSpecNames() {
        assertThat(ToastOccasion.values())
                .containsExactlyInAnyOrder(
                        ToastOccasion.BOOK_OPENED,
                        ToastOccasion.RUN_STARTED,
                        ToastOccasion.RUN_FINISHED,
                        ToastOccasion.RUN_FINISHED_FLAGGED,
                        ToastOccasion.PROJECT_NAME_CHANGED,
                        ToastOccasion.RETRY_REFUSED,
                        ToastOccasion.MODEL_LIST_UNREADABLE,
                        ToastOccasion.HIDDEN_WORK_FINISHED,
                        ToastOccasion.HIDDEN_WORK_REFUSED);
    }

    // IF an occasion raised nothing, or a toast of another severity, THEN its caller's message would be lost or
    // would read as something else.
    @ParameterizedTest
    @EnumSource(ToastOccasion.class)
    void raise_eachOccasion_raisesOneToastOfItsSeverity(final ToastOccasion occasion) {
        onFx(() -> toasts().raiseWithAction(occasion, anyAction(), 2, 2));

        assertThat(shownToasts()).hasSize(1);
        assertThat(shownToasts().get(0).getStyleClass())
                .contains(occasion.severity().styleClass());
    }

    // IF the retry refusal said nothing about the translation, THEN a person would not know to wait for it.
    @Test
    void raise_retryRefused_isAWarningThatNamesTheRunningTranslation() {
        onFx(() -> toasts().raise(ToastOccasion.RETRY_REFUSED));

        assertThat(shownToasts().get(0).getStyleClass()).contains("toast-warn");
        assertThat(textOf(shownToasts().get(0))).contains("translating");
    }

    // IF the automatic name change were silent, THEN a project would be renamed behind the person's back.
    @Test
    void raise_projectNameChanged_isAnInformationMessageNamingTheNewName() {
        onFx(() -> toasts().raise(ToastOccasion.PROJECT_NAME_CHANGED, "Earth Gravity · EN → UK"));

        assertThat(shownToasts().get(0).getStyleClass()).contains("toast-info");
        assertThat(textOf(shownToasts().get(0))).isEqualTo("Project name changed to Earth Gravity · EN → UK");
    }

    // IF hidden work ended unannounced, THEN a person who left it running would never know the results are ready.
    @Test
    void raiseWithAction_hiddenWorkFinished_isASuccessWithTheReviewAction() {
        onFx(() -> toasts().raiseWithAction(ToastOccasion.HIDDEN_WORK_FINISHED, anyAction(), "Names pass"));

        assertThat(shownToasts().get(0).getStyleClass()).contains("toast-ok");
        assertThat(textOf(shownToasts().get(0))).isEqualTo("Nothing opens by itself.");
        assertThat(shownToasts().get(0).lookupAll(".toast-action")).hasSize(1);
    }

    // IF the old start words survived anywhere, THEN a person would still be told progress is lost on closing.
    @Test
    void catalogue_everyToastKey_neverSaysProgressIsKeptOnlyUntilClose() {
        final List<MessageKey> keys =
                Stream.of(ToastOccasion.values()).map(ToastOccasion::key).toList();

        assertThat(keys)
                .extracting(key ->
                        injector.getInstance(ua.bookloom.ui.i18n.Messages.class).get(key, "x", 1))
                .noneMatch(text -> text.contains("Keep the application open"));
    }

    private static ToastAction anyAction() {
        return new ToastAction(MessageKey.WORK_HIDDEN_RESULTS, MessageKey.WORK_HIDDEN_RESULTS_TIP, () -> {});
    }
}
