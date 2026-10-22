package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.binding.BooleanBinding;
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

    // IF the freeze named no screen, THEN every screen would turn read-only while the pass writes to one.
    @Test
    void freezes_hiddenNamesPass_isTrueForNamesStyleOnly() {
        hiddenNamesPass();

        assertThat(hidden.freezes(ViewNames.NAMES_STYLE).get()).isTrue();
        assertThat(hidden.freezes(ViewNames.EXPORT).get()).isFalse();
    }

    // IF the pass still showed in the window, THEN its screen would not be read-only: the person sees the dialog.
    @Test
    void freezes_namesPassInTheForeground_isFalse() {
        tracker.begin(ActivityKind.NAMES_PASS, () -> {});

        assertThat(hidden.freezes(ViewNames.NAMES_STYLE).get()).isFalse();
    }

    // IF the binding did not follow the work, THEN the screen would stay read-only after the pass ended.
    @Test
    void freezes_hiddenPassEnds_turnsFalseAgain() {
        final ActivityTracker.Handle names = hiddenNamesPass();
        final BooleanBinding frozen = hidden.freezes(ViewNames.NAMES_STYLE);

        names.end();

        assertThat(frozen.get()).isFalse();
    }

    // IF the toast named no operation, THEN the person could not tell which finished pass it speaks of.
    @Test
    void finished_raisesTheReviewToastWithTheKindLabel() {
        hidden.finished(ActivityKind.NAMES_PASS, () -> {});

        assertThat(toasts.raised())
                .singleElement()
                .isEqualTo(new RecordingToasts.Raised(
                        "success", MessageKey.TOAST_HIDDEN_WORK_FINISHED, List.of("Preparing names")));
        assertThat(toasts.actions())
                .singleElement()
                .extracting(ToastAction::label, ToastAction::tip)
                .containsExactly(MessageKey.WORK_HIDDEN_RESULTS, MessageKey.WORK_HIDDEN_RESULTS_TIP);
    }

    // IF the toast action did not run what it was given, THEN "Review the results" would lead nowhere.
    @Test
    void finished_actionPressed_runsTheReview() {
        final AtomicInteger reviewed = new AtomicInteger();
        hidden.finished(ActivityKind.NAMES_PASS, reviewed::incrementAndGet);

        toasts.actions().getFirst().run().run();

        assertThat(reviewed.get()).isEqualTo(1);
    }
}
