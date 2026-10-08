package ua.bookloom.ui.screen;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.control.TabMovesFocus;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.Genre;
import ua.bookloom.ui.state.StyleSuggestionOutcome;

/**
 * The Tone &amp; style card: a genre box that suggests the forty predefined genres and also takes free text, the
 * register, the free-text narrative voice and audience, and who narrates (the person and, for a first-person narrator, the
 * gender).
 *
 * <p>The person sees a predefined genre in the interface language while the brief holds its English name, because the
 * prompt is written in English.
 */
@Slf4j
final class BriefToneCard {

    private static final int VOICE_ROWS = 2;
    private static final double FIELD_SPACING = 6;

    private final Messages messages;
    private final SearchableCombo<String> genre;
    private final BriefChoice<Register> register;
    private final BriefChoice<NarratorPerson> narrator;
    private final BriefChoice<Gender> narratorGender;
    private final TextArea voice = TabMovesFocus.install(new TextArea());
    private final TextField audience = new TextField();
    private final Node node;
    // True while a value from the view model is written into a control, so it is not handed back as a person's choice.
    private boolean applying;

    BriefToneCard(final BookBriefViewModel viewModel, final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.genre = genreBox(viewModel);
        this.register = registerChoice(viewModel, messages);
        Tips.install(messages, register.node(), MessageKey.BRIEF_TONE_REGISTER_TIP);
        this.narrator = narratorChoice(viewModel, messages);
        this.narratorGender = narratorGenderChoice(viewModel, messages);
        configureInputs(viewModel);
        final Label notice = narratorNotice(viewModel, messages);
        this.node = BriefCards.card(
                "brief-tone-card",
                messages,
                MessageKey.BRIEF_CARD_TONE,
                suggestion(viewModel, messages),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_GENRE, genre),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_REGISTER, register.withHelp()),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_VOICE, voice),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_AUDIENCE, audience),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_NARRATOR, narrator.withHelp()),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_NARRATOR_GENDER, narratorGender.withHelp()),
                notice);
    }

    private static BriefChoice<Register> registerChoice(final BookBriefViewModel viewModel, final Messages messages) {
        return new BriefChoice<>(
                "brief-tone-register",
                messages,
                List.of(
                        new BriefChoice.Option<>(
                                Register.FORMAL_LITERARY,
                                MessageKey.BRIEF_REGISTER_FORMAL,
                                MessageKey.BRIEF_HELP_REGISTER_FORMAL),
                        new BriefChoice.Option<>(
                                Register.NEUTRAL,
                                MessageKey.BRIEF_REGISTER_NEUTRAL,
                                MessageKey.BRIEF_HELP_REGISTER_NEUTRAL),
                        new BriefChoice.Option<>(
                                Register.CASUAL,
                                MessageKey.BRIEF_REGISTER_CASUAL,
                                MessageKey.BRIEF_HELP_REGISTER_CASUAL)),
                viewModel::setRegister);
    }

    // The model reads the opening of the book and fills these fields; the person reviews them before going on.
    private static Node suggestion(final BookBriefViewModel viewModel, final Messages messages) {
        if (!viewModel.styleSuggestion().isOffered()) {
            return new VBox();
        }
        final Button suggest = new Button(messages.get(MessageKey.BRIEF_SUGGEST));
        suggest.setId("brief-suggest-style");
        suggest.getStyleClass().add("btn-secondary");
        Tips.install(messages, suggest, MessageKey.BRIEF_SUGGEST_TIP);
        suggest.disableProperty().bind(viewModel.styleSuggestion().suggesting());
        suggest.setOnAction(event -> viewModel.styleSuggestion().ask());
        final Label outcome = new Label();
        outcome.setId("brief-suggest-outcome");
        outcome.getStyleClass().add("hint");
        outcome.setWrapText(true);
        outcome.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> outcomeText(viewModel.styleSuggestion().outcome().get(), messages),
                        viewModel.styleSuggestion().outcome()));
        BriefCards.shownWhile(outcome, outcome.textProperty().isNotEmpty());
        return new VBox(FIELD_SPACING, suggest, outcome);
    }

    private static String outcomeText(final StyleSuggestionOutcome outcome, final Messages messages) {
        return switch (outcome.kind()) {
            case NONE -> "";
            case DONE -> messages.get(MessageKey.BRIEF_SUGGEST_DONE);
            case NO_MODEL -> messages.get(MessageKey.BRIEF_SUGGEST_NO_MODEL);
            case BUSY -> messages.get(MessageKey.ACTIVITY_BLOCKED, messages.get(ActivityKind.SUGGEST_STYLE.label()));
            case FAILED -> outcome.message();
        };
    }

    private static Label narratorNotice(final BookBriefViewModel viewModel, final Messages messages) {
        final Label notice = BriefCards.hint(messages, MessageKey.BRIEF_NARRATOR_NOTICE);
        notice.setId("brief-tone-narrator-notice");
        Tips.install(messages, notice, MessageKey.BRIEF_NARRATOR_NOTICE_TIP);
        notice.visibleProperty().bind(viewModel.narratorNotice());
        notice.managedProperty().bind(notice.visibleProperty());
        return notice;
    }

    private static BriefChoice<NarratorPerson> narratorChoice(
            final BookBriefViewModel viewModel, final Messages messages) {
        final BriefChoice<NarratorPerson> choice = new BriefChoice<>(
                "brief-tone-narrator",
                messages,
                List.of(
                        new BriefChoice.Option<>(
                                NarratorPerson.UNSPECIFIED,
                                MessageKey.BRIEF_NARRATOR_UNSPECIFIED,
                                MessageKey.BRIEF_HELP_NARRATOR_UNSPECIFIED),
                        new BriefChoice.Option<>(
                                NarratorPerson.FIRST,
                                MessageKey.BRIEF_NARRATOR_FIRST,
                                MessageKey.BRIEF_HELP_NARRATOR_FIRST),
                        new BriefChoice.Option<>(
                                NarratorPerson.THIRD,
                                MessageKey.BRIEF_NARRATOR_THIRD,
                                MessageKey.BRIEF_HELP_NARRATOR_THIRD)),
                viewModel::setNarratorPerson);
        Tips.install(messages, choice.node(), MessageKey.BRIEF_TONE_NARRATOR_TIP);
        return choice;
    }

    private static BriefChoice<Gender> narratorGenderChoice(
            final BookBriefViewModel viewModel, final Messages messages) {
        final BriefChoice<Gender> choice = new BriefChoice<>(
                "brief-tone-narrator-gender",
                messages,
                List.of(
                        new BriefChoice.Option<>(
                                Gender.UNKNOWN,
                                MessageKey.BRIEF_NARRATOR_GENDER_UNKNOWN,
                                MessageKey.BRIEF_HELP_GENDER_UNKNOWN),
                        new BriefChoice.Option<>(
                                Gender.MALE, MessageKey.BRIEF_NARRATOR_GENDER_MALE, MessageKey.BRIEF_HELP_GENDER_MALE),
                        new BriefChoice.Option<>(
                                Gender.FEMALE,
                                MessageKey.BRIEF_NARRATOR_GENDER_FEMALE,
                                MessageKey.BRIEF_HELP_GENDER_FEMALE)),
                viewModel::setNarratorGender);
        Tips.install(messages, choice.node(), MessageKey.BRIEF_TONE_NARRATOR_GENDER_TIP);
        return choice;
    }

    private void configureInputs(final BookBriefViewModel viewModel) {
        voice.setId("brief-tone-voice");
        Tips.install(messages, voice, MessageKey.BRIEF_TONE_VOICE_TIP);
        voice.getStyleClass().add("brief-input");
        voice.setPromptText(messages.get(MessageKey.BRIEF_TONE_VOICE_PROMPT));
        voice.setPrefRowCount(VOICE_ROWS);
        voice.setWrapText(true);
        voice.textProperty().addListener((observed, was, now) -> typed(now, viewModel::setVoiceEra));
        audience.setId("brief-tone-audience");
        Tips.install(messages, audience, MessageKey.BRIEF_TONE_AUDIENCE_TIP);
        audience.setPromptText(messages.get(MessageKey.BRIEF_TONE_AUDIENCE_PROMPT));
        audience.textProperty().addListener((observed, was, now) -> typed(now, viewModel::setAudience));
    }

    private SearchableCombo<String> genreBox(final BookBriefViewModel viewModel) {
        final SearchableCombo<String> box = SearchableCombo.freeText(
                Arrays.stream(Genre.values()).map(this::genreName).toList(), Function.identity(), Function.identity());
        box.setId("brief-tone-genre");
        Tips.install(messages, box, MessageKey.BRIEF_TONE_GENRE_TIP);
        box.setPromptText(messages.get(MessageKey.BRIEF_TONE_GENRE_PROMPT));
        box.setMaxWidth(Double.MAX_VALUE);
        box.committedProperty().addListener((observed, was, now) -> genrePicked(viewModel, now));
        return box;
    }

    Node node() {
        return node;
    }

    void show(final BookBrief brief) {
        applying = true;
        try {
            final String stored = brief.genre();
            final String shown = stored == null ? null : Genre.toShown(stored, this::genreName);
            if (!Objects.equals(genre.getCommitted(), shown)) {
                genre.select(shown);
            }
            register.show(brief.register());
            narrator.show(brief.narrator().person());
            narratorGender.show(brief.narrator().gender());
            narratorGender.node().setDisable(brief.narrator().person() != NarratorPerson.FIRST);
            showText(voice, brief.voiceEra());
            showText(audience, brief.audience());
        } finally {
            applying = false;
        }
    }

    private String genreName(final Genre genre) {
        return messages.get(genre.messageKey());
    }

    private void genrePicked(final BookBriefViewModel viewModel, final @Nullable String shown) {
        if (applying) {
            return;
        }
        final String text = shown == null ? "" : shown;
        viewModel.setGenre(Genre.toStored(text, this::genreName));
    }

    private void typed(final String text, final Consumer<String> apply) {
        if (!applying) {
            apply.accept(text);
        }
    }

    private static void showText(final TextInputControl input, final @Nullable String text) {
        final String wanted = text == null ? "" : text;
        if (!wanted.equals(input.getText())) {
            input.setText(wanted);
        }
    }
}
