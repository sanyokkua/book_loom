package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The brief as it is drawn while a book is open: the five live cards in two columns and the footer that moves around the workflow.
 *
 * <p>Split from the controller because the controller's other job is swapping this for the no-book state. The cards
 * write a person's choice into the view model, and the view model's brief is written back into the cards, so each card
 * guards against handing a published value straight back. The view model outlives this view, so it is observed
 * through a weak listener held by the field below; the cards' own listeners capture the cards, which is what keeps
 * them reachable for as long as their nodes are on show.
 */
@Slf4j
final class BriefView {

    // The content width, in px, below which two columns of cards would be squeezed until their controls are cut.
    private static final double STACK_BELOW = 700;
    private static final double CARD_SPACING = 14;

    private final BriefParts parts;
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

    BriefView(final BriefParts parts) {
        Objects.requireNonNull(parts, "parts");
        final BookBriefViewModel viewModel = parts.viewModel();
        this.messages = parts.messages();
        this.navigator = parts.navigator();
        this.progress = parts.progress();
        this.parts = parts;
        log.debug("building the brief");
        this.languages = new BriefLanguagesCard(parts);
        this.tone = new BriefToneCard(viewModel, messages, parts.runs().view());
        this.policies = new BriefPoliciesCard(viewModel, messages);
        this.also = new BriefAlsoCard(viewModel, messages);
        this.quality = new BriefQualityCard(parts);
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
        final VBox left = column("brief-column-left", languages.node(), policies.node(), also.node(), quality.node());
        final VBox right = column("brief-column-right", tone.node());
        final TwoColumns columns = new TwoColumns(left, right, STACK_BELOW);
        columns.setId("brief-columns");
        columns.disableProperty().bind(parts.lock().readOnly());
        return new VBox(CARD_SPACING, subtitle, readOnlyBanner(), columns, footer);
    }

    // While a translation is at work the whole brief is read-only; the banner stays outside the columns so Pause works.
    private Banner readOnlyBanner() {
        final Banner banner = BriefCards.shownWhile(
                new Banner(
                        "brief-readonly-banner",
                        Banner.Role.WARN,
                        ImportViews.WARNING_GLYPH,
                        messages.get(MessageKey.BRIEF_READ_ONLY_BANNER),
                        messages.get(MessageKey.BRIEF_READ_ONLY_NOTE)),
                parts.lock().readOnly());
        final Button pause = banner.addTrailingAction(
                "brief-readonly-pause", messages.get(MessageKey.BRIEF_READ_ONLY_PAUSE), this::pause);
        Tips.install(messages, pause, MessageKey.BRIEF_READ_ONLY_PAUSE_TIP);
        final ReadOnlyObjectProperty<Controls> controls = parts.translating().controls();
        pause.disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !controls.get().pause().isEnabled(), controls));
        return banner;
    }

    private void pause() {
        log.debug("pause pressed on the read-only banner");
        parts.translating().pause();
    }

    // The person's decisions on the left, which the model never touches; what it predicts on the right.
    private static VBox column(final String id, final Node... cards) {
        final VBox column = new VBox(CARD_SPACING, cards);
        column.setId(id);
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
