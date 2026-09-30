package ua.bookloom.ui.screen;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.UnitPolicy;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;

/** The Translation policies card: the four policy choices and the faithful-to-natural balance slider. */
final class BriefPoliciesCard {

    private static final double PAIR_SPACING = 14;
    private static final double BALANCE_MAX = 100;
    private static final double BALANCE_START = 55;

    private final BriefChoice<NamePolicy> names;
    private final BriefChoice<ForeignPassagePolicy> foreign;
    private final BriefChoice<FootnotePolicy> footnotes;
    private final BriefChoice<UnitPolicy> units;
    private final Slider balance = new Slider(0, BALANCE_MAX, BALANCE_START);
    private final Node node;
    // True while a value from the view model is written into the slider, so it is not handed back as a person's choice.
    private boolean applying;

    BriefPoliciesCard(final BookBriefViewModel viewModel, final Messages messages) {
        this.names = nameChoice(viewModel, messages);
        this.foreign = foreignChoice(viewModel, messages);
        this.footnotes = footnoteChoice(viewModel, messages);
        this.units = unitChoice(viewModel, messages);
        balance.setId("brief-policy-balance");
        Tips.install(messages, balance, MessageKey.BRIEF_POLICY_BALANCE_TIP);
        Tips.install(messages, names.node(), MessageKey.BRIEF_POLICY_NAMES_TIP);
        Tips.install(messages, foreign.node(), MessageKey.BRIEF_POLICY_FOREIGN_TIP);
        Tips.install(messages, footnotes.node(), MessageKey.BRIEF_POLICY_FOOTNOTES_TIP);
        Tips.install(messages, units.node(), MessageKey.BRIEF_POLICY_UNITS_TIP);
        balance.valueProperty().addListener((observed, was, now) -> dragged(viewModel, now.doubleValue()));
        this.node = BriefCards.card(
                "brief-policies-card",
                messages,
                MessageKey.BRIEF_CARD_POLICIES,
                BriefCards.field(messages, MessageKey.BRIEF_POLICY_NAMES, names.node()),
                BriefCards.field(
                        messages,
                        MessageKey.BRIEF_POLICY_FOREIGN,
                        foreign.node(),
                        MessageKey.BRIEF_POLICY_FOREIGN_HINT),
                footnotesAndUnits(messages),
                BriefCards.field(
                        messages, MessageKey.BRIEF_POLICY_BALANCE, balance, MessageKey.BRIEF_POLICY_BALANCE_HINT));
    }

    private static BriefChoice<FootnotePolicy> footnoteChoice(
            final BookBriefViewModel viewModel, final Messages messages) {
        return new BriefChoice<>(
                "brief-policy-footnotes",
                messages,
                List.of(
                        new BriefChoice.Option<>(FootnotePolicy.TRANSLATE, MessageKey.BRIEF_OPTION_TRANSLATE),
                        new BriefChoice.Option<>(FootnotePolicy.KEEP, MessageKey.BRIEF_OPTION_KEEP)),
                viewModel::setFootnotes);
    }

    private static BriefChoice<UnitPolicy> unitChoice(final BookBriefViewModel viewModel, final Messages messages) {
        return new BriefChoice<>(
                "brief-policy-units",
                messages,
                List.of(
                        new BriefChoice.Option<>(UnitPolicy.KEEP, MessageKey.BRIEF_OPTION_KEEP),
                        new BriefChoice.Option<>(UnitPolicy.METRIC, MessageKey.BRIEF_OPTION_METRIC)),
                viewModel::setUnits);
    }

    private static BriefChoice<NamePolicy> nameChoice(final BookBriefViewModel viewModel, final Messages messages) {
        return new BriefChoice<>(
                "brief-policy-names",
                messages,
                List.of(
                        new BriefChoice.Option<>(NamePolicy.TRANSLATE, MessageKey.BRIEF_OPTION_TRANSLATE),
                        new BriefChoice.Option<>(NamePolicy.TRANSLITERATE, MessageKey.BRIEF_OPTION_TRANSLITERATE),
                        new BriefChoice.Option<>(NamePolicy.KEEP_ORIGINAL, MessageKey.BRIEF_OPTION_KEEP_ORIGINAL)),
                viewModel::setNames);
    }

    private static BriefChoice<ForeignPassagePolicy> foreignChoice(
            final BookBriefViewModel viewModel, final Messages messages) {
        return new BriefChoice<>(
                "brief-policy-foreign",
                messages,
                List.of(
                        new BriefChoice.Option<>(ForeignPassagePolicy.KEEP, MessageKey.BRIEF_OPTION_KEEP_AS_IS),
                        new BriefChoice.Option<>(ForeignPassagePolicy.TRANSLATE, MessageKey.BRIEF_OPTION_TRANSLATE),
                        new BriefChoice.Option<>(
                                ForeignPassagePolicy.TRANSLATE_WITH_NOTE, MessageKey.BRIEF_OPTION_TRANSLATE_NOTE)),
                viewModel::setForeignPassages);
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        names.show(brief.names());
        foreign.show(brief.foreignPassages());
        footnotes.show(brief.footnotes());
        units.show(brief.units());
        if ((int) Math.round(balance.getValue()) != brief.balance()) {
            applying = true;
            try {
                balance.setValue(brief.balance());
            } finally {
                applying = false;
            }
        }
    }

    private Node footnotesAndUnits(final Messages messages) {
        final VBox left = BriefCards.field(messages, MessageKey.BRIEF_POLICY_FOOTNOTES, footnotes.node());
        final VBox right = BriefCards.field(messages, MessageKey.BRIEF_POLICY_UNITS, units.node());
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        return new HBox(PAIR_SPACING, left, right);
    }

    private void dragged(final BookBriefViewModel viewModel, final double value) {
        if (!applying) {
            viewModel.setBalance((int) Math.round(value));
        }
    }
}
