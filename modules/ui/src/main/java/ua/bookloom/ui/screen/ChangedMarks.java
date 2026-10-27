package ua.bookloom.ui.screen;

import javafx.beans.binding.Bindings;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeMarks;

/** The filter of the last operation's marks: it keeps only the changed rows, which the table marks by their left edge. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChangedMarks {

    /** The "Changed in last run" switch, offered only while some row is marked and on while the filter is. */
    static ToggleButton filter(final Messages messages, final String id, final ChangeMarks.Side side) {
        final ToggleButton filter = Tips.install(
                messages, new ToggleButton(messages.get(MessageKey.CHANGED_FILTER)), MessageKey.CHANGED_FILTER_TIP);
        filter.setId(id);
        filter.getStyleClass().add("btn-ghost");
        filter.setMinWidth(Region.USE_PREF_SIZE);
        filter.setMaxHeight(Double.MAX_VALUE);
        filter.selectedProperty().bindBidirectional(side.onlyChanged());
        Visibility.bind(filter, Bindings.createBooleanBinding(() -> side.size() > 0, side.revision()));
        return filter;
    }
}
