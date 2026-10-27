package ua.bookloom.ui.screen;

import java.util.List;
import java.util.stream.Collectors;
import javafx.scene.control.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelAdvice;
import ua.bookloom.ui.state.Tone;

/** Fills the Providers tab's model note: the advice texts one per line, coloured by the first note's level. */
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ModelNoteLabel {

    static void show(final Label label, final List<ModelAdvice.Note> notes, final Messages messages) {
        log.debug("showing {} model note(s)", notes.size());
        label.getStyleClass().removeAll(Tone.statusClasses());
        label.setText(notes.stream().map(note -> messages.get(note.text())).collect(Collectors.joining("\n")));
        notes.stream()
                .findFirst()
                .ifPresent(
                        first -> label.getStyleClass().add(toneOf(first.level()).statusClass()));
        Visibility.show(label, !notes.isEmpty());
    }

    private static Tone toneOf(final ModelAdvice.Level level) {
        return switch (level) {
            case RECOMMENDED -> Tone.OK;
            case MINIMUM, INFO -> Tone.INFO;
            case WARNING -> Tone.WARN;
        };
    }
}
