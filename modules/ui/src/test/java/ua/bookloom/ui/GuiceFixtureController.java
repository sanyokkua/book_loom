package ua.bookloom.ui;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ua.bookloom.ui.i18n.Messages;

/**
 * A controller that exists only to prove {@link GuiceControllerFactory} builds controllers through Guice: its
 * constructor asks for a service no FXML loader could supply by itself.
 */
public final class GuiceFixtureController {

    private final Messages messages;

    @FXML
    private Label greeting;

    /**
     * Receives the singleton the injector owns.
     *
     * @param messages the catalogue resolver
     */
    // The FXML loader assigns the labelled field after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public GuiceFixtureController(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    Messages messages() {
        return messages;
    }

    Label greeting() {
        return greeting;
    }
}
