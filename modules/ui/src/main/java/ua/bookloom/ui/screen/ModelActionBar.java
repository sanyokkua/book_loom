package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The Scan · Review · Translate · Stop bar both cards of Names &amp; style carry, so the two lists are driven the same
 * way. Only one model action runs at a time across both cards, so a running action disables all six buttons and shows
 * Stop on both, whichever card started it.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ModelActionBar {

    private static final double SPACING = 8;

    /**
     * What a card's three actions do.
     *
     * @param scan finds more rows
     * @param review asks the model which rows to keep
     * @param translate asks the model for the missing targets or renderings
     */
    record Actions(Runnable scan, Runnable review, Runnable translate) {}

    /**
     * The words of one card's three actions; Stop reads the same on both cards.
     *
     * @param scan the caption of Scan
     * @param scanTip its hover explanation
     * @param review the caption of Review
     * @param reviewTip its hover explanation
     * @param translate the caption of Translate
     * @param translateTip its hover explanation
     */
    record Words(
            MessageKey scan,
            MessageKey scanTip,
            MessageKey review,
            MessageKey reviewTip,
            MessageKey translate,
            MessageKey translateTip) {}

    /**
     * Builds the bar of one card.
     *
     * @param messages the catalogue the captions and explanations come from
     * @param card the card's id prefix, which the buttons' ids extend: {@code card-scan}, {@code card-review},
     *     {@code card-translate} and {@code card-stop}
     * @param words the captions and hover explanations of the three actions
     * @param actions what the three actions do
     * @param names the screen's state, whose stop ends whichever action runs
     * @return the bar, whose id is {@code card-actions}
     */
    static HBox build(
            final Messages messages,
            final String card,
            final Words words,
            final Actions actions,
            final NamesStyleViewModel names) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(card, "card");
        final Button scan = button(messages, card + "-scan", words.scan(), words.scanTip(), actions.scan());
        final Button review = button(messages, card + "-review", words.review(), words.reviewTip(), actions.review());
        final Button translate =
                button(messages, card + "-translate", words.translate(), words.translateTip(), actions.translate());
        final Button stop =
                button(messages, card + "-stop", MessageKey.BAR_STOP, MessageKey.BAR_STOP_TIP, names::stopModel);
        for (final Button model : new Button[] {scan, review, translate}) {
            model.disableProperty()
                    .bind(names.busy().or(names.modelBlockedReason().isNotEmpty()));
        }
        stop.visibleProperty().bind(names.busy());
        stop.managedProperty().bind(names.busy());
        final HBox bar = new HBox(SPACING, scan, review, translate, stop);
        bar.setId(card + "-actions");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinWidth(Region.USE_PREF_SIZE);
        return bar;
    }

    private static Button button(
            final Messages messages,
            final String id,
            final MessageKey caption,
            final MessageKey tip,
            final Runnable onPress) {
        final Button button = Tips.install(messages, new Button(messages.get(caption)), tip);
        button.setId(id);
        button.getStyleClass().add("btn-ghost");
        // A caption is never cut to an ellipsis: the toolbar wraps the bar onto the next line instead.
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> {
            log.debug("{} pressed", id);
            onPress.run();
        });
        return button;
    }
}
