package ua.bookloom.ui.screen;

import java.util.Locale;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ExitGuard;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.LanguageChoice;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.SystemLanguage;
import ua.bookloom.ui.state.AppLanguage;

/**
 * The language choice of the Appearance tab: the chooser, the Restart now button that appears only when the choice
 * differs from the language on screen, and the lines for a restart or a save that did not work. Restarting goes through
 * the quit question, so a translation under way is never killed unasked.
 *
 * <p>{@link AppLanguage} outlives the nodes, so it is observed through weak listeners; this row is kept in the chooser's
 * properties, so it lives exactly as long as the nodes.
 */
@Slf4j
final class AppearanceLanguageRow {

    private static final String OWNER_KEY = "bookloom.languageRow";

    private final AppLanguage language;
    private final ExitGuard exitGuard;
    private final Messages messages;
    private final SystemLanguage system;
    private final ComboBox<LanguageChoice> chooser;
    private final Label restartError;
    private final ChangeListener<LanguageChoice> onStored = (observed, was, now) -> show(now);
    private final ChangeListener<LanguageChoice> onPicked = (observed, was, now) -> picked(now);

    AppearanceLanguageRow(
            final AppLanguage language,
            final ExitGuard exitGuard,
            final Messages messages,
            final SystemLanguage system,
            final ComboBox<LanguageChoice> chooser,
            final Button restart,
            final Label restartError,
            final Label saveError,
            final ObservableBooleanValue themeSaveFailed) {
        this.language = Objects.requireNonNull(language, "language");
        this.exitGuard = Objects.requireNonNull(exitGuard, "exitGuard");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.system = Objects.requireNonNull(system, "system");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.restartError = Objects.requireNonNull(restartError, "restartError");
        buildChooser();
        chooser.getProperties().put(OWNER_KEY, this);
        Visibility.bind(restart, language.restartNeededProperty());
        restart.disableProperty().bind(language.restartingProperty());
        // From the press until the window closes, the button says the restart is under way.
        restart.textProperty()
                .bind(Bindings.when(language.restartingProperty())
                        .then(messages.get(MessageKey.APPEARANCE_RESTARTING))
                        .otherwise(messages.get(MessageKey.APPEARANCE_RESTART)));
        restart.setOnAction(event -> restartNow());
        Tips.install(messages, restart, MessageKey.APPEARANCE_RESTART_TIP);
        hide(restartError);
        Visibility.bind(saveError, language.saveFailedProperty().or(themeSaveFailed));
    }

    private void buildChooser() {
        chooser.getItems().setAll(LanguageChoice.values());
        chooser.setConverter(new StringConverter<>() {
            @Override
            public String toString(final LanguageChoice choice) {
                return choice == null ? "" : nameOf(choice);
            }

            @Override
            public LanguageChoice fromString(final String text) {
                throw new UnsupportedOperationException("the chooser is not editable");
            }
        });
        chooser.setValue(language.choiceProperty().get());
        Tips.install(messages, chooser, MessageKey.APPEARANCE_LANGUAGE_TIP);
        chooser.valueProperty().addListener(onPicked);
        language.choiceProperty().addListener(new WeakChangeListener<>(onStored));
    }

    private String nameOf(final LanguageChoice choice) {
        return switch (choice) {
            case SYSTEM -> messages.get(MessageKey.APPEARANCE_LANGUAGE_SYSTEM, computerLanguage());
            case ENGLISH -> messages.get(MessageKey.APPEARANCE_LANGUAGE_EN);
            case UKRAINIAN -> messages.get(MessageKey.APPEARANCE_LANGUAGE_UK);
        };
    }

    // The language the computer would give, in the words of the language on screen.
    private String computerLanguage() {
        final Locale resolved = LanguageChoice.SYSTEM.resolve(system.current());
        return resolved.getDisplayLanguage(messages.locale());
    }

    private void picked(final LanguageChoice choice) {
        if (choice != null && choice != language.choiceProperty().get()) {
            log.debug("language {} chosen", choice);
            language.choose(choice);
        }
    }

    private void show(final LanguageChoice choice) {
        if (chooser.getValue() != choice) {
            chooser.setValue(choice);
        }
    }

    private void restartNow() {
        hide(restartError);
        final Runnable go = () -> language.restart(this::showRestartError);
        if (exitGuard.requestClose(go)) {
            log.debug("restart asked with nothing to lose");
            go.run();
        }
    }

    private void showRestartError() {
        log.debug("restart did not start; telling the person");
        restartError.setVisible(true);
        restartError.setManaged(true);
    }

    private static void hide(final Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }
}
