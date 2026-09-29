package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The names and style screen's frame: the heading and subtitle from the view, the note that the step can be skipped,
 * and the footer that goes back to the structure or starts the run.
 *
 * <p>Start translation asks {@link TranslatingViewModel#start()} and then shows the translating screen whatever came of
 * it: the view model begins a run only when the controls offer a start, so with a run already under way the screen just
 * shows that run, and a missing input is named on the translating screen's banner.
 */
@Slf4j
public final class NamesStyleController {

    private final Messages messages;
    private final Navigator navigator;
    private final TranslatingViewModel translating;

    @FXML
    private Pane body;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param messages the catalogue the built parts are worded from
     * @param navigator where Back and Start translation lead
     * @param translating what Start translation asks to begin the run
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public NamesStyleController(
            final Messages messages, final Navigator navigator, final TranslatingViewModel translating) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.translating = Objects.requireNonNull(translating, "translating");
    }

    @FXML
    void initialize() {
        log.debug("building the names and style screen");
        final Banner skip =
                new Banner("names-style-banner", Banner.Role.INFO, "ℹ", "", messages.get(MessageKey.NAMES_STYLE_SKIP));
        final StepFooter footer = StepFooter.of(
                new StepFooter.Action(
                        "names-style-back",
                        messages.get(MessageKey.NAMES_STYLE_BACK),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.STRUCTURE)),
                new StepFooter.Action(
                        "names-style-start", messages.get(MessageKey.NAMES_STYLE_START), "btn-primary", this::onStart));
        body.getChildren().setAll(skip, footer);
    }

    private void onStart() {
        log.debug(
                "start translation pressed, a start is offered: {}",
                translating.controls().get().start().isEnabled());
        translating.start();
        navigator.navigate(ViewNames.TRANSLATING);
    }
}
