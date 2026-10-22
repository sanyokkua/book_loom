package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.control.TextMatch;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.BriefLock;

/**
 * The Languages card: two searchable boxes over the same list, the source one led by Auto, where the brief names no
 * language and the model identifies it. A name or tag the list does not hold is parsed, so any language the application
 * can name is accepted, and text that names none leaves the previous choice. While the source is Auto, the language the
 * model found in its passages is offered beside a "Use it" button and is never applied without the click.
 */
@Slf4j
final class BriefLanguagesCard {

    private static final double FIELD_SPACING = 5;
    private static final double PROPOSAL_SPACING = 8;
    // Not a language tag, so no tag of the list or typed by a person can be taken for it.
    private static final String AUTO = "auto";

    private final SearchableCombo<String> source;
    private final SearchableCombo<String> target;
    private final Node node;
    // True while a value from the view model is written into a box, so it is not handed back as a person's choice.
    private boolean applying;

    BriefLanguagesCard(final BriefParts parts) {
        final BookBriefViewModel viewModel = parts.viewModel();
        final Messages messages = parts.messages();
        final LanguageNames names = parts.names();
        final Locale locale = messages.locale();
        final List<String> tags = names.list(locale);
        this.source = Tips.install(
                messages,
                sourceBox(withAuto(tags), names, locale, messages.get(MessageKey.BRIEF_SOURCE_AUTO)),
                MessageKey.BRIEF_SOURCE_LABEL_TIP);
        this.target = Tips.install(
                messages,
                box("brief-target", tags, tag -> names.nameOf(tag, locale), text -> names.parse(text, locale)),
                MessageKey.BRIEF_TARGET_LABEL_TIP);
        source.committedProperty().addListener((observed, was, now) -> pickedSource(now, viewModel));
        target.committedProperty().addListener((observed, was, now) -> picked(now, viewModel::setTargetLanguage));
        this.node = card(parts);
        source.disableProperty().bind(parts.lock().languagesLocked());
        target.disableProperty().bind(parts.lock().languagesLocked());
    }

    private Node card(final BriefParts parts) {
        final Messages messages = parts.messages();
        final BookBriefViewModel viewModel = parts.viewModel();
        return BriefCards.card(
                "brief-languages-card",
                messages,
                MessageKey.BRIEF_CARD_LANGUAGES,
                new VBox(
                        FIELD_SPACING,
                        BriefCards.field(messages, MessageKey.BRIEF_SOURCE_LABEL, source),
                        undeclared(messages, viewModel),
                        proposal(viewModel, messages, parts.names())),
                new VBox(
                        FIELD_SPACING,
                        BriefCards.field(messages, MessageKey.BRIEF_TARGET_LABEL, target),
                        required(messages, target),
                        untested(messages, viewModel)),
                sameLanguage(messages, viewModel),
                locked(messages, parts.lock()),
                new BriefProjectLine(messages, parts.project()).node());
    }

    // Once translating has started the pair is fixed; the hint names the way to another language.
    private static Label locked(final Messages messages, final BriefLock lock) {
        final Label hint = BriefCards.shownWhile(
                BriefCards.hint(messages, MessageKey.BRIEF_LANGUAGES_LOCKED), lock.languagesLocked());
        hint.setId("brief-languages-locked");
        return hint;
    }

    private static Label undeclared(final Messages messages, final BookBriefViewModel viewModel) {
        final Label undeclared = BriefCards.shownWhile(
                BriefCards.hint(messages, MessageKey.BRIEF_SOURCE_UNDECLARED), viewModel.sourceUndeclared());
        undeclared.setId("brief-source-undeclared");
        return undeclared;
    }

    // The model's read of an Auto source: the person decides, so the source changes only when the button is pressed.
    private static Node proposal(
            final BookBriefViewModel viewModel, final Messages messages, final LanguageNames names) {
        final ReadOnlyObjectProperty<@Nullable String> proposed =
                viewModel.styleSuggestion().languageProposal();
        final Label text = BriefCards.hint(messages, MessageKey.BRIEF_SOURCE_PROPOSAL);
        text.setId("brief-source-proposal-text");
        text.textProperty()
                .bind(Bindings.createStringBinding(() -> proposalText(proposed.get(), messages, names), proposed));
        HBox.setHgrow(text, Priority.ALWAYS);
        final Button use = new Button(messages.get(MessageKey.BRIEF_SOURCE_USE));
        use.setId("brief-source-use");
        use.getStyleClass().add("btn-secondary");
        Tips.install(messages, use, MessageKey.BRIEF_SOURCE_USE_TIP);
        use.setOnAction(event -> useProposed(viewModel, proposed.get()));
        final HBox row = new HBox(PROPOSAL_SPACING, text, use);
        row.setId("brief-source-proposal");
        row.setAlignment(Pos.CENTER_LEFT);
        return BriefCards.shownWhile(
                row,
                Bindings.createBooleanBinding(
                        () -> proposed.get() != null
                                && viewModel.sourceUndeclared().get(),
                        proposed,
                        viewModel.sourceUndeclared()));
    }

    private static String proposalText(final @Nullable String tag, final Messages messages, final LanguageNames names) {
        return tag == null ? "" : messages.get(MessageKey.BRIEF_SOURCE_PROPOSAL, names.nameOf(tag, messages.locale()));
    }

    private static void useProposed(final BookBriefViewModel viewModel, final @Nullable String tag) {
        if (tag == null) {
            return;
        }
        log.debug("the proposed source language {} is taken", tag);
        viewModel.setSourceLanguage(tag);
    }

    private static Banner sameLanguage(final Messages messages, final BookBriefViewModel viewModel) {
        return BriefCards.shownWhile(
                new Banner(
                        "brief-languages-same",
                        Banner.Role.WARN,
                        ImportViews.WARNING_GLYPH,
                        "",
                        messages.get(MessageKey.BRIEF_LANGUAGES_SAME)),
                viewModel.sameLanguage());
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

    // A language with no tested rules still translates, with the general rules; the note says so instead of leaving a
    // person to wonder why its quotes or gender come out worse than Ukrainian's.
    private static Label untested(final Messages messages, final BookBriefViewModel viewModel) {
        final Label untested = BriefCards.shownWhile(
                BriefCards.hint(messages, MessageKey.BRIEF_TARGET_UNTESTED), viewModel.targetUntested());
        untested.setId("brief-target-untested");
        return Tips.install(messages, untested, MessageKey.BRIEF_TARGET_UNTESTED_TIP);
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        applying = true;
        try {
            showTag(source, brief.sourceLanguage() == null ? AUTO : brief.sourceLanguage());
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

    private void pickedSource(final @Nullable String tag, final BookBriefViewModel viewModel) {
        if (AUTO.equals(tag)) {
            if (!applying) {
                log.debug("Auto picked in the source box");
                viewModel.setSourceAuto();
            }
            return;
        }
        picked(tag, viewModel::setSourceLanguage);
    }

    private void picked(final @Nullable String tag, final Consumer<String> apply) {
        if (applying || tag == null) {
            return;
        }
        log.debug("language {} picked in a box", tag);
        apply.accept(tag);
    }

    private static List<String> withAuto(final List<String> tags) {
        final List<String> all = new ArrayList<>(tags.size() + 1);
        all.add(AUTO);
        all.addAll(tags);
        return all;
    }

    // The word "Auto" alone, or the whole entry, names the first entry; anything else is a language name or tag.
    private static SearchableCombo<String> sourceBox(
            final List<String> items, final LanguageNames names, final Locale locale, final String autoName) {
        final String autoWord = autoName.split("\\s", 2)[0];
        return box(
                "brief-source",
                items,
                tag -> AUTO.equals(tag) ? autoName : names.nameOf(tag, locale),
                text -> isAuto(text, autoName, autoWord) ? Optional.of(AUTO) : names.parse(text, locale));
    }

    private static boolean isAuto(final String text, final String autoName, final String autoWord) {
        final String typed = TextMatch.normalize(text.strip());
        return typed.equals(TextMatch.normalize(autoName)) || typed.equals(TextMatch.normalize(autoWord));
    }

    private static SearchableCombo<String> box(
            final String id,
            final List<String> tags,
            final Function<String, String> name,
            final Function<String, Optional<String>> parse) {
        final SearchableCombo<String> box = SearchableCombo.parsed(tags, name, parse);
        box.setId(id);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
