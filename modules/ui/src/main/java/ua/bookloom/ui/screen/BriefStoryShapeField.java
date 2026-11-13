package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.StoryShape;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.BriefLock;

/**
 * The tone card's story shape and the Chapter summaries switch under it (design D6): the shape the analysis found,
 * marked likely until the person picks it, is the reason the switch is suggested on or off, so the two sit together.
 * The shape is hidden while no analysis has read one; the switch is always shown.
 */
final class BriefStoryShapeField {

    private static final double SPACING = 12;

    private final BriefChoice<StoryShape> choice;
    private final Label likely;
    private final Node field;
    private final Node node;

    BriefStoryShapeField(final BookBriefViewModel viewModel, final BriefLock lock, final Messages messages) {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(messages, "messages");
        this.choice = new BriefChoice<>(
                "brief-tone-story-shape",
                messages,
                List.of(
                        new BriefChoice.Option<>(StoryShape.ONE_STORY, MessageKey.BRIEF_STORY_SHAPE_ONE),
                        new BriefChoice.Option<>(StoryShape.SEPARATE_STORIES, MessageKey.BRIEF_STORY_SHAPE_SEPARATE)),
                shape -> viewModel.summaries().setStoryShape(shape));
        Tips.install(messages, choice.node(), MessageKey.BRIEF_TONE_STORY_SHAPE_TIP);
        this.likely = BriefCards.likelyMark(messages, "brief-likely-story-shape");
        this.field = BriefCards.field(
                messages.get(MessageKey.BRIEF_TONE_STORY_SHAPE), "brief-tone-story-shape-label", choice.node(), likely);
        field.setId("brief-tone-story-shape-field");
        final ChapterSummariesSwitch summaries =
                new ChapterSummariesSwitch("brief-summaries", true, viewModel, lock, messages);
        this.node = new VBox(SPACING, field, summaries.node());
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        final StoryShape shape = brief.storyShape();
        Visibility.show(field, shape != null);
        Visibility.show(likely, shape != null && brief.likely().contains(BriefField.STORY_SHAPE));
        if (shape != null) {
            choice.show(shape);
        }
    }
}
