package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BriefProject;

/**
 * The project name line of the languages card: the name the project is stored under, whether it follows the languages
 * or was typed, and a Rename… link that asks the same question as the Projects screen. The name is always the stored
 * one, never a name worked out here.
 */
final class BriefProjectLine {

    private static final double SPACING = 4;
    private static final double ROW_SPACING = 8;

    private final VBox node;

    BriefProjectLine(final Messages messages, final BriefProject project) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(project, "project");
        final Label label = new Label(messages.get(MessageKey.BRIEF_PROJECT_NAME_LABEL));
        label.getStyleClass().add("muted");
        final Label name = new Label();
        name.setId("brief-project-name");
        name.getStyleClass().add("brief-project-name");
        name.textProperty().bind(project.name());
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final Button rename = new Button(messages.get(MessageKey.BRIEF_PROJECT_NAME_RENAME));
        rename.setId("brief-rename");
        rename.getStyleClass().add("btn-ghost");
        Tips.install(messages, rename, MessageKey.BRIEF_PROJECT_NAME_RENAME_TIP);
        rename.setOnAction(event -> project.rename());
        final HBox row = new HBox(ROW_SPACING, label, name, spacer, rename);
        row.setAlignment(Pos.CENTER_LEFT);
        final Label origin = origin(messages, project);
        node = new VBox(SPACING, row, origin);
        node.setId("brief-project-line");
    }

    private static Label origin(final Messages messages, final BriefProject project) {
        final Label origin = new Label();
        origin.setId("brief-project-origin");
        origin.getStyleClass().add("hint");
        origin.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                project.isAutomatic().get()
                                        ? MessageKey.BRIEF_PROJECT_NAME_AUTOMATIC
                                        : MessageKey.BRIEF_PROJECT_NAME_NAMED),
                        project.isAutomatic()));
        return origin;
    }

    VBox node() {
        return node;
    }
}
