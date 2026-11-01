package ua.bookloom.ui.screen;

import static ua.bookloom.util.contract.ArgNames.DEFAULT;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefChoices;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.QuoteStyle;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BriefPolicyKeys;

/**
 * The rows of the Translation policies card the target language's file sets (design D6): the apostrophe, the
 * quotation marks, the default address for strangers and for family, and one row per declared convention. A row is
 * built only for what the file sets, so an English target shows none, and the rows are built again when the target
 * changes. A brief that stores no value shows the file's default.
 */
@Slf4j
final class BriefKeyRows {

    private static final double ROW_SPACING = 12;
    // Words for the conventions the interface knows; any other reads by its key.
    private static final Map<String, Words> CONVENTION_WORDS =
            Map.of("hardG", new Words(MessageKey.BRIEF_CONVENTION_HARD_G, MessageKey.BRIEF_CONVENTION_HARD_G_TIP));

    private record Words(MessageKey label, MessageKey tip) {}

    private record Mark(BriefField field, Label label) {}

    private final Messages messages;
    private final BriefPolicyKeys keys;
    private final VBox node = new VBox(ROW_SPACING);
    private final List<Mark> marks = new ArrayList<>();
    private final List<Runnable> showers = new ArrayList<>();
    private BriefChoices built = BriefChoices.NONE;
    private @Nullable BookBrief shown;

    BriefKeyRows(final BriefPolicyKeys keys, final Messages messages) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.messages = Objects.requireNonNull(messages, "messages");
        node.setId("brief-policy-keys");
        node.setFillWidth(true);
        keys.choices().addListener((observed, was, now) -> rebuild(now));
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        shown = Objects.requireNonNull(brief, "brief");
        if (!built.equals(keys.choices().get())) {
            rebuild(keys.choices().get());
            return;
        }
        showers.forEach(Runnable::run);
        marks.forEach(mark -> Visibility.show(mark.label(), brief.likely().contains(mark.field())));
    }

    private void rebuild(final BriefChoices choices) {
        built = choices;
        node.getChildren().clear();
        marks.clear();
        showers.clear();
        log.debug(
                "policy key rows rebuilt: apostrophes {}, conventions {}",
                choices.apostrophes(),
                choices.conventions());
        if (!choices.apostrophes().isEmpty()) {
            apostropheRow(choices);
        }
        if (!choices.quoteStyles().isEmpty()) {
            quoteRow(choices);
        }
        final BriefChoices.AddressWords words = choices.address();
        if (words != null) {
            addressRows(choices, words);
        }
        choices.conventions().forEach(this::conventionRow);
        final BookBrief brief = shown;
        if (brief != null) {
            show(brief);
        }
    }

    private void apostropheRow(final BriefChoices choices) {
        final List<BriefChoice.Segment<String>> segments = choices.apostrophes().stream()
                .map(mark -> new BriefChoice.Segment<>(
                        mark,
                        messages.get(
                                MessageKey.BRIEF_POLICY_APOSTROPHE_SEGMENT,
                                mark,
                                String.format(Locale.ROOT, "%04X", mark.codePointAt(0))),
                        messages.get(
                                MessageKey.BRIEF_POLICY_APOSTROPHE_OPTION,
                                mark,
                                String.format(Locale.ROOT, "%04X", mark.codePointAt(0)))))
                .toList();
        final BriefChoice<String> choice = new BriefChoice<>("brief-policy-apostrophe", segments, keys::setApostrophe);
        addRow(
                MessageKey.BRIEF_POLICY_APOSTROPHE,
                MessageKey.BRIEF_POLICY_APOSTROPHE_TIP,
                choice,
                BriefField.APOSTROPHE);
        showers.add(() -> choice.show(Objects.requireNonNullElse(
                current().apostrophe(), Objects.requireNonNull(choices.defaultApostrophe(), DEFAULT))));
    }

    private void quoteRow(final BriefChoices choices) {
        final List<BriefChoice.Segment<QuoteStyle>> segments = choices.quoteStyles().stream()
                .map(style -> new BriefChoice.Segment<>(
                        style,
                        outer(style) + " " + inner(style),
                        messages.get(MessageKey.BRIEF_POLICY_QUOTES_OPTION, outer(style), inner(style))))
                .toList();
        final BriefChoice<QuoteStyle> choice = new BriefChoice<>("brief-policy-quotes", segments, keys::setQuoteStyle);
        addRow(MessageKey.BRIEF_POLICY_QUOTES, MessageKey.BRIEF_POLICY_QUOTES_TIP, choice, BriefField.QUOTE_STYLE);
        showers.add(() -> choice.show(Objects.requireNonNullElse(
                current().quoteStyle(), Objects.requireNonNull(choices.defaultQuoteStyle(), DEFAULT))));
    }

    private void addressRows(final BriefChoices choices, final BriefChoices.AddressWords words) {
        final Address fallback = Objects.requireNonNull(choices.defaultAddress(), DEFAULT);
        final BriefChoice<AddressForm> strangers =
                addressChoice("brief-policy-address-strangers", words, keys::setStrangersAddress);
        final BriefChoice<AddressForm> family =
                addressChoice("brief-policy-address-family", words, keys::setFamilyAddress);
        addRow(
                MessageKey.BRIEF_POLICY_ADDRESS_STRANGERS,
                MessageKey.BRIEF_POLICY_ADDRESS_STRANGERS_TIP,
                strangers,
                BriefField.DEFAULT_ADDRESS);
        addRow(
                MessageKey.BRIEF_POLICY_ADDRESS_FAMILY,
                MessageKey.BRIEF_POLICY_ADDRESS_FAMILY_TIP,
                family,
                BriefField.DEFAULT_ADDRESS);
        showers.add(() -> {
            final Address address = Objects.requireNonNullElse(current().defaultAddress(), fallback);
            strangers.show(address.strangers());
            family.show(address.family());
        });
    }

    private BriefChoice<AddressForm> addressChoice(
            final String id, final BriefChoices.AddressWords words, final Consumer<AddressForm> onPick) {
        return new BriefChoice<>(
                id,
                List.of(
                        new BriefChoice.Segment<>(
                                AddressForm.FORMAL,
                                words.formal(),
                                messages.get(MessageKey.BRIEF_POLICY_ADDRESS_FORMAL, words.formal())),
                        new BriefChoice.Segment<>(
                                AddressForm.INFORMAL,
                                words.informal(),
                                messages.get(MessageKey.BRIEF_POLICY_ADDRESS_INFORMAL, words.informal()))),
                onPick);
    }

    private void conventionRow(final BriefChoices.DeclaredConvention convention) {
        final List<BriefChoice.Segment<String>> segments = convention.choices().stream()
                .map(value -> new BriefChoice.Segment<>(
                        value, value, messages.get(MessageKey.BRIEF_CONVENTION_OPTION, value)))
                .toList();
        final String id = "brief-policy-convention-" + convention.key();
        final BriefChoice<String> choice =
                new BriefChoice<>(id, segments, value -> keys.setConvention(convention.key(), value));
        final Words words = CONVENTION_WORDS.get(convention.key());
        final String label = words == null
                ? messages.get(MessageKey.BRIEF_CONVENTION_OTHER, convention.key())
                : messages.get(words.label());
        Tips.install(messages, choice.node(), words == null ? MessageKey.BRIEF_CONVENTION_OTHER_TIP : words.tip());
        add(label, id, choice, BriefField.CONVENTIONS);
        showers.add(() -> choice.show(current().conventions().getOrDefault(convention.key(), convention.fallback())));
    }

    private void addRow(
            final MessageKey label, final MessageKey tip, final BriefChoice<?> choice, final BriefField field) {
        Tips.install(messages, choice.node(), tip);
        add(messages.get(label), choice.node().getId(), choice, field);
    }

    private void add(final String label, final String id, final BriefChoice<?> choice, final BriefField field) {
        final Label mark = BriefCards.likelyMark(messages, id + "-likely");
        marks.add(new Mark(field, mark));
        node.getChildren().add(BriefCards.field(label, id + "-label", choice.node(), mark));
    }

    private BookBrief current() {
        return Objects.requireNonNull(shown, "a brief is shown before its rows");
    }

    private static String outer(final QuoteStyle style) {
        return style.outerOpen() + Messages.ELLIPSIS + style.outerClose();
    }

    private static String inner(final QuoteStyle style) {
        return style.innerOpen() + Messages.ELLIPSIS + style.innerClose();
    }
}
