package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The brief as it is drawn while a book is open: the five live cards and the footer that moves around the workflow.
 *
 * <p>Split from the controller because the controller's other job is swapping this for the no-book state. The cards
 * write a person's choice into the view model, and the view model's brief is written back into the cards, so each card
 * guards against handing a published value straight back. The view model outlives this view, so it is observed
 * through a weak listener held by the field below; the cards' own listeners capture the cards, which is what keeps
 * them reachable for as long as their nodes are on show.
 */
@Slf4j
final class BriefView {

    private static final double COLUMN_SPACING = 14;
    private static final double COLUMN_WIDTH = 300;
    private static final double CARD_SPACING = 14;

    private final Messages messages;
    private final Navigator navigator;
    private final WorkflowProgress progress;
    private final BriefLanguagesCard languages;
    private final BriefToneCard tone;
    private final BriefPoliciesCard policies;
    private final BriefAlsoCard also;
    private final BriefQualityCard quality;
    private final StepFooter footer;
    private final Node root;
    private final ChangeListener<@Nullable BookBrief> onBrief = (observed, was, now) -> show(now);

    BriefView(
            final BookBriefViewModel viewModel,
            final Messages messages,
            final Navigator navigator,
            final LanguageNames names,
            final SettingsViewModel settings,
            final WorkflowProgress progress) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.progress = Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(viewModel, "viewModel");
        log.debug("building the brief");
        this.languages = new BriefLanguagesCard(viewModel, messages, names);
        this.tone = new BriefToneCard(viewModel, messages);
        this.policies = new BriefPoliciesCard(viewModel, messages);
        this.also = new BriefAlsoCard(viewModel, messages);
        this.quality = new BriefQualityCard(viewModel, settings, messages, navigator);
        this.footer = footer();
        this.root = build();
        viewModel.brief().addListener(new WeakChangeListener<>(onBrief));
        viewModel.canContinue().addListener((observed, was, now) -> allowContinue(now));
        allowContinue(viewModel.canContinue().get());
        show(viewModel.brief().get());
    }

    // A disabled button shows no tooltip, so the footer says on a line beside it what is missing.
    private void allowContinue(final boolean allowed) {
        footer.forwardButton().setDisable(!allowed);
        footer.explainDisabledForward(allowed ? null : messages.get(MessageKey.NAV_LOCKED_NO_LANGUAGES));
    }

    Node root() {
        return root;
    }

    private Node build() {
        final Label subtitle = ImportViews.wrapped(messages.get(MessageKey.BRIEF_SUBTITLE), "muted");
        subtitle.setId("brief-subtitle");
        final VBox left = column(languages.node(), tone.node());
        final VBox right = column(policies.node(), also.node(), quality.node());
        return new VBox(CARD_SPACING, subtitle, new HBox(COLUMN_SPACING, left, right), footer);
    }

    private static VBox column(final Node... cards) {
        final VBox column = new VBox(CARD_SPACING, cards);
        column.setPrefWidth(COLUMN_WIDTH);
        column.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }

    private StepFooter footer() {
        return StepFooter.of(
                new StepFooter.Action(
                        "brief-back",
                        messages.get(MessageKey.BRIEF_BACK),
                        messages.get(MessageKey.BRIEF_BACK_TIP),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.IMPORT)),
                new StepFooter.Action(
                        "brief-continue",
                        messages.get(MessageKey.BRIEF_CONTINUE),
                        messages.get(MessageKey.BRIEF_CONTINUE_TIP),
                        "btn-primary",
                        this::onContinue));
    }

    private void show(final @Nullable BookBrief brief) {
        if (brief == null) {
            log.debug("no brief to show: the book was closed");
            return;
        }
        languages.show(brief);
        tone.show(brief);
        policies.show(brief);
        also.show(brief);
        quality.show(brief);
    }

    private void onContinue() {
        final Optional<ViewNames> next = navigator.nextAvailableStep(ViewNames.BOOK_BRIEF);
        log.debug("continue pressed, the next step is {}", next);
        progress.markDone(ViewNames.BOOK_BRIEF);
        next.ifPresent(navigator::navigate);
    }
}
