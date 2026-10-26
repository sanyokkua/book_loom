package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.RunMode;

/**
 * The one button above both cards of Names &amp; style that prepares the names and the recurring terms in one pass,
 * with "Confirm suggested genders" beside it, and the plan's estimate for the brief's dial under it. While it runs the
 * shared progress dialog shows the progress, and that dialog's Cancel is the pass's Stop; the pass changes nothing when
 * stopped. The estimate is asked of the port again when a change of the dial has been saved, since the port reads the
 * stored brief; until then the line is blank, because the minutes it held were computed for the previous dial. The
 * view models outlive this view, so what listens to them does so weakly, with the strong reference held by the
 * subtitle.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AutoNamesBar {

    private static final double SPACING = 4;
    private static final double CARD_SPACING = 12;
    private static final String LISTENERS_KEY = "names-style-auto-listeners";

    static HBox build(
            final Messages messages,
            final NamesStyleViewModel names,
            final BookBriefViewModel brief,
            final ObservableBooleanValue frozen) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(frozen, "frozen");
        final Button auto = button(messages, names, frozen);
        final Label title = new Label(messages.get(MessageKey.NAMES_STYLE_AUTO));
        title.getStyleClass().add("card-title");
        final Label about = new Label(messages.get(MessageKey.NAMES_STYLE_AUTO_ABOUT));
        about.getStyleClass().add("muted");
        about.setWrapText(true);
        final Label subtitle = new Label();
        subtitle.setId("names-style-auto-subtitle");
        subtitle.getStyleClass().add("muted");
        follow(messages, names, brief, subtitle);
        final VBox texts = new VBox(SPACING, title, about, subtitle);
        HBox.setHgrow(texts, Priority.ALWAYS);
        final HBox bar = new HBox(CARD_SPACING, texts, auto, confirmGenders(messages, names, frozen));
        bar.setId("names-style-auto-bar");
        bar.getStyleClass().addAll("card", "card-warm");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static Button button(
            final Messages messages, final NamesStyleViewModel names, final ObservableBooleanValue frozen) {
        final Button auto = Tips.install(
                messages, new Button(messages.get(MessageKey.NAMES_STYLE_AUTO)), MessageKey.NAMES_STYLE_AUTO_TIP);
        auto.setId("names-style-auto");
        auto.getStyleClass().add("btn-primary");
        auto.setMinWidth(Region.USE_PREF_SIZE);
        ModelActionBar.explainWhenBlocked(messages, auto, MessageKey.NAMES_STYLE_AUTO_TIP, names, frozen);
        auto.setOnAction(event -> {
            log.debug("names-style-auto pressed");
            names.autoPrepare();
        });
        return auto;
    }

    // Shown only while a character's gender is a suggestion; off while a model action could replace what it confirms.
    private static Button confirmGenders(
            final Messages messages, final NamesStyleViewModel names, final ObservableBooleanValue frozen) {
        final Button confirm = Tips.install(
                messages,
                new Button(messages.get(MessageKey.NAMES_STYLE_CONFIRM_GENDERS)),
                MessageKey.NAMES_STYLE_CONFIRM_GENDERS_TIP);
        confirm.setId("names-style-confirm-genders");
        confirm.getStyleClass().add("btn-ghost");
        confirm.setMinWidth(Region.USE_PREF_SIZE);
        confirm.disableProperty().bind(names.busy().or(frozen));
        Visibility.bind(confirm, names.suggestedGenderCount().greaterThan(0));
        confirm.setOnAction(event -> {
            log.debug("names-style-confirm-genders pressed");
            names.confirmGenders();
        });
        return confirm;
    }

    private static void follow(
            final Messages messages,
            final NamesStyleViewModel names,
            final BookBriefViewModel brief,
            final Label line) {
        final Runnable show = () -> line.setText(
                estimateLine(messages, names.autoEstimate().get(), brief.brief().get()));
        final ChangeListener<@Nullable NamesEstimate> onEstimate = (observed, was, now) -> show.run();
        final ChangeListener<@Nullable BookBrief> onBrief = (observed, was, now) -> {
            if (was != null && now != null && was.dial() != now.dial()) {
                log.debug("the dial changed from {} to {}: the old estimate is dropped", was.dial(), now.dial());
                names.clearAutoEstimate();
            }
            show.run();
        };
        final ChangeListener<Boolean> onSaved = (observed, was, saving) -> {
            if (!saving) {
                log.debug("the brief was saved: asking for the estimate again");
                names.refreshAutoEstimate();
            }
        };
        line.getProperties().put(LISTENERS_KEY, new Object[] {onEstimate, onBrief, onSaved});
        names.autoEstimate().addListener(new WeakChangeListener<>(onEstimate));
        brief.brief().addListener(new WeakChangeListener<>(onBrief));
        brief.saving().addListener(new WeakChangeListener<>(onSaved));
        show.run();
    }

    // Blank while the port cannot say, so the line never promises minutes nobody computed.
    private static String estimateLine(
            final Messages messages, final @Nullable NamesEstimate estimate, final @Nullable BookBrief brief) {
        if (estimate == null || brief == null) {
            return "";
        }
        final String dial = messages.get(RunMode.dialKey(brief.dial()));
        return estimate.minutesLow() == estimate.minutesHigh()
                ? messages.get(MessageKey.NAMES_STYLE_AUTO_ESTIMATE_ONE, estimate.minutesLow(), dial)
                : messages.get(
                        MessageKey.NAMES_STYLE_AUTO_ESTIMATE, estimate.minutesLow(), estimate.minutesHigh(), dial);
    }
}
