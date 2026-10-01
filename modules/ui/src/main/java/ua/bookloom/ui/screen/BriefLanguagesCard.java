package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;

/**
 * The Languages card: two searchable boxes over the same list. A name or tag the list does not hold is parsed, so any
 * language the application can name is accepted, and text that names none leaves the previous choice.
 */
@Slf4j
final class BriefLanguagesCard {

    private static final double FIELD_SPACING = 5;

    private final SearchableCombo<String> source;
    private final SearchableCombo<String> target;
    private final Node node;
    // True while a value from the view model is written into a box, so it is not handed back as a person's choice.
    private boolean applying;

    BriefLanguagesCard(final BookBriefViewModel viewModel, final Messages messages, final LanguageNames names) {
        final Locale locale = messages.locale();
        final List<String> tags = names.list(locale);
        this.source =
                Tips.install(messages, box("brief-source", tags, names, locale), MessageKey.BRIEF_SOURCE_LABEL_TIP);
        this.target =
                Tips.install(messages, box("brief-target", tags, names, locale), MessageKey.BRIEF_TARGET_LABEL_TIP);
        source.committedProperty().addListener((observed, was, now) -> picked(now, viewModel::setSourceLanguage));
        target.committedProperty().addListener((observed, was, now) -> picked(now, viewModel::setTargetLanguage));
        final Label undeclared = BriefCards.shownWhile(
                BriefCards.hint(messages, MessageKey.BRIEF_SOURCE_UNDECLARED), viewModel.sourceUndeclared());
        undeclared.setId("brief-source-undeclared");
        final Banner same = BriefCards.shownWhile(
                new Banner(
                        "brief-languages-same",
                        Banner.Role.WARN,
                        ImportViews.WARNING_GLYPH,
                        "",
                        messages.get(MessageKey.BRIEF_LANGUAGES_SAME)),
                viewModel.sameLanguage());
        final Label required = required(messages, target);
        this.node = BriefCards.card(
                "brief-languages-card",
                messages,
                MessageKey.BRIEF_CARD_LANGUAGES,
                new VBox(FIELD_SPACING, BriefCards.field(messages, MessageKey.BRIEF_SOURCE_LABEL, source), undeclared),
                new VBox(FIELD_SPACING, BriefCards.field(messages, MessageKey.BRIEF_TARGET_LABEL, target), required),
                same);
    }

    // Nothing can be translated without a target, so an empty one says so under the box until one is chosen.
    private static Label required(final Messages messages, final SearchableCombo<String> target) {
        final Label required = BriefCards.shownWhile(
                BriefCards.hint(messages, MessageKey.BRIEF_TARGET_REQUIRED),
                target.committedProperty().isNull());
        required.setId("brief-target-required");
        required.getStyleClass().add("field-required");
        return required;
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        applying = true;
        try {
            showTag(source, brief.sourceLanguage());
            showTag(target, brief.targetLanguage());
        } finally {
            applying = false;
        }
    }

    private static void showTag(final SearchableCombo<String> box, final @Nullable String tag) {
        if (!Objects.equals(box.getCommitted(), tag)) {
            box.select(tag);
        }
    }

    private void picked(final @Nullable String tag, final Consumer<String> apply) {
        if (applying || tag == null) {
            return;
        }
        log.debug("language {} picked in a box", tag);
        apply.accept(tag);
    }

    private static SearchableCombo<String> box(
            final String id, final List<String> tags, final LanguageNames names, final Locale locale) {
        final SearchableCombo<String> box =
                SearchableCombo.parsed(tags, tag -> names.nameOf(tag, locale), text -> names.parse(text, locale));
        box.setId(id);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
