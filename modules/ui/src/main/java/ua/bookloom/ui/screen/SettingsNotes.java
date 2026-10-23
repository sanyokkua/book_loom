package ua.bookloom.ui.screen;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

/** The notes the settings areas share: shown only while their condition holds, and worded from a launch value. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SettingsNotes {

    /** Shows {@code label} only while {@code shown} holds, taking no room otherwise. */
    static void show(final Label label, final ObservableValue<Boolean> shown) {
        label.visibleProperty().bind(shown);
        label.managedProperty().bind(label.visibleProperty());
    }

    /** Whether a translation is under way, from the first call to its end. */
    static BooleanBinding runUnderWay(final StateMirror mirror) {
        return Bindings.createBooleanBinding(() -> isUnderWay(mirror.runState().get()), mirror.runState());
    }

    private static boolean isUnderWay(final RunState state) {
        return state != RunState.IDLE && !state.hasEnded();
    }

    /** Words {@code note} from the launch value named by {@code source}, and shows it only while one is set. */
    static void overrideNote(
            final Messages messages, final Label note, final ObservableValue<String> source, final MessageKey wording) {
        note.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            final String name = source.getValue();
                            return name == null ? "" : messages.get(wording, name);
                        },
                        source));
        show(note, source.map(name -> name != null));
    }
}
