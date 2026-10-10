package ua.bookloom.ui.screen;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeMarks;

/** The two parts of the last operation's marks a card shows: the chip on a changed row and the filter that keeps only them. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChangedMarks {

    /** A chip for a row the last operation added or changed; the word is the cue as well as the colour. */
    static Label chip(final Messages messages) {
        final Label chip = Tips.installOnHover(
                messages, new Label(messages.get(MessageKey.CHANGED_MARKER)), MessageKey.CHANGED_MARKER_TIP);
        chip.getStyleClass().addAll("chip", "chip-info", "changed-marker");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /** The "Changed in last run" switch, offered only while some row is marked and on while the filter is. */
    static ToggleButton filter(final Messages messages, final String id, final ChangeMarks.Side side) {
        final ToggleButton filter = Tips.install(
                messages, new ToggleButton(messages.get(MessageKey.CHANGED_FILTER)), MessageKey.CHANGED_FILTER_TIP);
        filter.setId(id);
        filter.getStyleClass().add("btn-ghost");
        filter.setMinWidth(Region.USE_PREF_SIZE);
        filter.selectedProperty().bindBidirectional(side.onlyChanged());
        filter.visibleProperty().bind(Bindings.createBooleanBinding(() -> side.size() > 0, side.revision()));
        filter.managedProperty().bind(filter.visibleProperty());
        return filter;
    }
}
