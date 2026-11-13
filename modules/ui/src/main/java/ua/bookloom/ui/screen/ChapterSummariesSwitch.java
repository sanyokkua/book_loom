package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.StoryShape;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.BriefLock;

/**
 * The Chapter summaries switch, the same on the Book Brief and on the Translating ready card: both are built from the
 * one brief, so turning it in one place shows in the other. Under it, what the story shape suggests (none when no
 * analysis ran) and, after the person turned it on, how many finished chapters will be summarised first. It follows
 * {@link BriefLock}: read-only while a translation is at work, editable while paused.
 *
 * <p>The view model outlives the screens, so its brief and count are observed through weak listeners held by the
 * node. Nothing here logs: the view model logs each change.
 */
final class ChapterSummariesSwitch {

    private static final double SPACING = 4;
    private static final String KEEPER = "bookloom.summaries.listeners";

    private final Messages messages;
    private final ToggleSwitch toggle;
    private final Label suggested;
    private final Label pending;
    private final VBox node;
    // True while the brief's value is written into the switch, so it is not handed back as the person's choice.
    private boolean applying;

    /**
     * Builds the switch.
     *
     * @param id the switch's id; its two lines take it as a prefix
     * @param labelled whether the switch carries its own words, or stands in a row whose caption names it
     * @param viewModel the brief the switch reads and writes
     * @param lock what makes it read-only while a translation is at work
     * @param messages the catalogue the words come from
     */
    ChapterSummariesSwitch(
            final String id,
            final boolean labelled,
            final BookBriefViewModel viewModel,
            final BriefLock lock,
            final Messages messages) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.toggle = Tips.install(
                messages,
                labelled ? new ToggleSwitch(messages.get(MessageKey.BRIEF_SUMMARIES)) : new BareSwitch(),
                MessageKey.BRIEF_SUMMARIES_TIP);
        toggle.setId(id);
        toggle.setAccessibleText(messages.get(MessageKey.BRIEF_SUMMARIES));
        toggle.setMinWidth(Region.USE_PREF_SIZE);
        toggle.disableProperty().bind(Objects.requireNonNull(lock, "lock").readOnly());
        toggle.selectedProperty().addListener((observed, was, now) -> {
            if (!applying) {
                viewModel.summaries().setChapterSummaries(now);
            }
        });
        this.suggested = line(id + "-suggested");
        this.pending = line(id + "-pending");
        this.node = new VBox(SPACING, toggle, suggested, pending);
        follow(viewModel);
    }

    private static Label line(final String id) {
        final Label line = Wrapped.wholeLines(new Label());
        line.setId(id);
        line.getStyleClass().add("hint");
        Visibility.show(line, false);
        return line;
    }

    private void follow(final BookBriefViewModel viewModel) {
        final ChangeListener<@Nullable BookBrief> onBrief = (observed, was, now) -> show(now);
        final ChangeListener<Number> onPending = (observed, was, now) -> showPending(now.intValue());
        node.getProperties().put(KEEPER, new Object[] {onBrief, onPending});
        viewModel.brief().addListener(new WeakChangeListener<>(onBrief));
        viewModel.summaries().pending().addListener(new WeakChangeListener<>(onPending));
        show(viewModel.brief().get());
        showPending(viewModel.summaries().pending().get());
    }

    /** {@return the switch with its suggestion and count lines under it} */
    Node node() {
        return node;
    }

    private void show(final @Nullable BookBrief brief) {
        if (brief == null) {
            return;
        }
        applying = true;
        try {
            toggle.setSelected(brief.summariesOn());
        } finally {
            applying = false;
        }
        final StoryShape shape = brief.storyShape();
        Visibility.show(suggested, shape != null);
        if (shape != null) {
            final String words = messages.get(
                    shape == StoryShape.ONE_STORY
                            ? MessageKey.BRIEF_SUMMARIES_SUGGESTED_ON
                            : MessageKey.BRIEF_SUMMARIES_SUGGESTED_OFF);
            suggested.setText(
                    brief.likely().contains(BriefField.STORY_SHAPE)
                            ? words + " " + messages.get(MessageKey.BRIEF_LIKELY)
                            : words);
        }
    }

    private void showPending(final int chapters) {
        pending.setText(chapters > 0 ? messages.get(MessageKey.BRIEF_SUMMARIES_PENDING, chapters) : "");
        Visibility.show(pending, chapters > 0);
    }

    /**
     * A switch with no words of its own, for a row whose caption names it. The skin's preferred width keeps a 20 px gap
     * before the track even with no label, which would set the track off from the values in the rows above it, so
     * the switch prefers its minimum: the track alone.
     */
    private static final class BareSwitch extends ToggleSwitch {

        @Override
        protected double computePrefWidth(final double height) {
            return computeMinWidth(height);
        }
    }
}
