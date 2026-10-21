package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ToastAction;

/** Long work in the background: the screen it writes to, and other model work refused with a way back to it. */
class HiddenWorkTest {

    private final ActivityTracker tracker = new ActivityTracker(new StateMirror());
    private final RecordingToasts toasts = new RecordingToasts();
    private final HiddenWork hidden =
            new HiddenWork(tracker, toasts, new Messages((LocaleProvider) () -> Locale.ENGLISH));

    private ActivityTracker.Handle hiddenNamesPass() {
        final ActivityTracker.Handle names = tracker.begin(ActivityKind.NAMES_PASS, () -> {});
        tracker.hide(names.id());
        return names;
    }

    // IF the hidden pass named no screen, THEN Names & style could not turn read-only while it writes there.
    @Test
    void screen_namesPassHidden_isNamesStyle() {
        hiddenNamesPass();

        assertThat(hidden.screen().get()).isEqualTo(ViewNames.NAMES_STYLE);
    }

    @Test
    void screen_nothingHidden_isNull() {
        tracker.begin(ActivityKind.NAMES_PASS, () -> {});

        assertThat(hidden.screen().get()).isNull();
    }

    // IF a second job started beside the hidden pass, THEN two requests would queue in the one model.
    @Test
    void refuses_suggestStyleWhileNamesHidden_raisesShowProgressWarning() {
        hiddenNamesPass();

        final boolean refused = hidden.refuses(ActivityKind.SUGGEST_STYLE);

        assertThat(refused).isTrue();
        assertThat(toasts.raised())
                .singleElement()
                .isEqualTo(new RecordingToasts.Raised(
                        "warning", MessageKey.WORK_HIDDEN_REFUSED, List.of("Preparing names")));
        assertThat(toasts.actions())
                .singleElement()
                .extracting(ToastAction::label)
                .isEqualTo(MessageKey.WORK_HIDDEN_SHOW);
    }

    // IF "Show progress" did not bring the pass back, THEN the toast would offer a way that leads nowhere.
    @Test
    void showProgress_pressed_revealsTheHiddenWork() {
        final ActivityTracker.Handle names = hiddenNamesPass();
        hidden.refuses(ActivityKind.SUGGEST_STYLE);

        toasts.actions().getFirst().run().run();

        assertThat(tracker.blockingActivity().get()).extracting(Activity::id).isEqualTo(names.id());
    }

    // IF work that does not compete for the model were refused, THEN a model listing would be blocked for nothing.
    @Test
    void refuses_modelListingWhileNamesHidden_isFalseAndSaysNothing() {
        hiddenNamesPass();

        assertThat(hidden.refuses(ActivityKind.MODEL_LISTING)).isFalse();
        assertThat(toasts.raised()).isEmpty();
    }
}
