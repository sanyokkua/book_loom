package ua.bookloom.ui.screen;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;

/** The Also translate card: one switch for each kind of auxiliary text. */
final class BriefAlsoCard {

    private static final double FIELD_SPACING = 5;

    private final ToggleSwitch navigation;
    private final ToggleSwitch alt;
    private final ToggleSwitch metadata;
    private final ToggleSwitch frontmatter;
    private final Node node;
    // True while a value from the view model is written into a switch, so it is not handed back as a person's choice.
    private boolean applying;

    BriefAlsoCard(final BookBriefViewModel viewModel, final Messages messages) {
        this.navigation =
                toggle("brief-aux-nav", messages, MessageKey.BRIEF_AUX_NAV, MessageKey.BRIEF_AUX_NAV_TIP, viewModel);
        this.alt = toggle("brief-aux-alt", messages, MessageKey.BRIEF_AUX_ALT, MessageKey.BRIEF_AUX_ALT_TIP, viewModel);
        this.metadata = toggle(
                "brief-aux-metadata",
                messages,
                MessageKey.BRIEF_AUX_METADATA,
                MessageKey.BRIEF_AUX_METADATA_TIP,
                viewModel);
        this.frontmatter = toggle(
                "brief-aux-frontmatter",
                messages,
                MessageKey.BRIEF_AUX_FRONTMATTER,
                MessageKey.BRIEF_AUX_FRONTMATTER_TIP,
                viewModel);
        final Label frontmatterHint = BriefCards.hint(messages, MessageKey.BRIEF_AUX_FRONTMATTER_HINT);
        this.node = BriefCards.card(
                "brief-aux-card",
                messages,
                MessageKey.BRIEF_CARD_ALSO,
                navigation,
                alt,
                metadata,
                new VBox(FIELD_SPACING, frontmatter, frontmatterHint));
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        final AlsoTranslate wanted = brief.alsoTranslate();
        applying = true;
        try {
            navigation.setSelected(wanted.navigationLabels());
            alt.setSelected(wanted.altText());
            metadata.setSelected(wanted.metadata());
            frontmatter.setSelected(wanted.frontmatter());
        } finally {
            applying = false;
        }
    }

    private ToggleSwitch toggle(
            final String id,
            final Messages messages,
            final MessageKey label,
            final MessageKey tip,
            final BookBriefViewModel viewModel) {
        final ToggleSwitch toggle = Tips.install(messages, new ToggleSwitch(messages.get(label)), tip);
        toggle.setId(id);
        toggle.selectedProperty().addListener((observed, was, now) -> switched(viewModel));
        return toggle;
    }

    private void switched(final BookBriefViewModel viewModel) {
        if (!applying) {
            viewModel.setAlsoTranslate(new AlsoTranslate(
                    navigation.isSelected(), alt.isSelected(), metadata.isSelected(), frontmatter.isSelected()));
        }
    }
}
