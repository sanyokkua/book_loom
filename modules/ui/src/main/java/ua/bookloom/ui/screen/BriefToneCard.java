package ua.bookloom.ui.screen;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Genre;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.control.TabMovesFocus;
import ua.bookloom.ui.control.TextLimit;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.ErrorWords;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.GenreLabels;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.StyleSuggestionOutcome;

/**
 * The Tone &amp; style card: a genre box that suggests the forty predefined genres and also takes free text, the
 * register, the free-text narrative voice and audience, and who narrates (the person and, for a first-person narrator, the
 * gender). Under each field the model suggested, its evidence: the book's words it quoted and how many passages agree,
 * or an "uncertain" chip.
 *
 * <p>The person sees a predefined genre in the interface language while the brief holds its English name, because the
 * prompt is written in English.
 */
@Slf4j
final class BriefToneCard {

    private static final int VOICE_ROWS = 2;
    // Every model call carries these three, so each is capped at what a genre, an audience or a note on the voice
    // needs.
    private static final int GENRE_MAX = 100;
    private static final int AUDIENCE_MAX = 200;
    private static final int VOICE_MAX = 500;
    private static final double FIELD_SPACING = 6;

    private final Messages messages;
    private final SearchableCombo<String> genre;
    private final BriefChoice<Register> register;
    private final BriefChoice<NarratorPerson> narrator;
    private final BriefChoice<Gender> narratorGender;
    private final TextArea voice = TabMovesFocus.install(new TextArea());
    private final TextField audience = new TextField();
    private final Map<BriefField, BriefEvidenceRow> evidence;
    private final Label genderSuggested = new Label();
    private final BookBriefViewModel viewModel;
    private final Node node;
    // True while a value from the view model is written into a control, so it is not handed back as a person's choice.
    private boolean applying;

    BriefToneCard(
            final BookBriefViewModel viewModel, final Messages messages, final ObservableValue<RunStateView> runRow) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.evidence = evidenceRows(messages);
        configureGenderSuggested();
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
                suggestion(viewModel, messages, Objects.requireNonNull(runRow, "runRow")),
                field(MessageKey.BRIEF_TONE_GENRE, genre, BriefField.GENRE),
                field(MessageKey.BRIEF_TONE_REGISTER, register.withHelp(), BriefField.REGISTER),
                field(MessageKey.BRIEF_TONE_VOICE, voice, BriefField.VOICE),
                field(MessageKey.BRIEF_TONE_AUDIENCE, audience, BriefField.AUDIENCE),
                field(MessageKey.BRIEF_TONE_NARRATOR, narrator.withHelp(), BriefField.NARRATOR),
                genderField(),
                notice);
        viewModel
                .styleSuggestion()
                .evidence()
                .addListener(
                        (observed, was, now) -> showEvidence(viewModel.brief().get()));
    }

    // The gender choice, its "(suggested)" mark and the evidence line under them.
    private Node genderField() {
        return field(
                MessageKey.BRIEF_TONE_NARRATOR_GENDER,
                new VBox(FIELD_SPACING, narratorGender.withHelp(), genderSuggested),
                BriefField.NARRATOR_GENDER);
    }

    private void configureGenderSuggested() {
        genderSuggested.setId("brief-tone-narrator-gender-suggested");
        genderSuggested.getStyleClass().add("hint");
        Tips.install(messages, genderSuggested, MessageKey.BRIEF_NARRATOR_GENDER_SUGGESTED_TIP);
        Visibility.show(genderSuggested, false);
    }

    // The gender the model proved reads "Male (suggested)" until the person changes it.
    private void showGenderSuggested(final BookBrief brief) {
        final boolean suggested = viewModel.styleSuggestion().isNarratorGenderSuggested(brief);
        if (suggested != genderSuggested.isVisible()) {
            log.debug(
                    "narrator gender {} shown as suggested: {}",
                    brief.narrator().gender(),
                    suggested);
        }
        Visibility.show(genderSuggested, suggested);
        if (suggested) {
            final MessageKey gender = brief.narrator().gender() == Gender.MALE
                    ? MessageKey.BRIEF_NARRATOR_GENDER_MALE
                    : MessageKey.BRIEF_NARRATOR_GENDER_FEMALE;
            genderSuggested.setText(messages.get(MessageKey.BRIEF_NARRATOR_GENDER_SUGGESTED, messages.get(gender)));
        }
    }

    private static Map<BriefField, BriefEvidenceRow> evidenceRows(final Messages messages) {
        final Map<BriefField, BriefEvidenceRow> rows = new EnumMap<>(BriefField.class);
        for (final BriefField field : BriefField.values()) {
            rows.put(field, new BriefEvidenceRow(messages, field));
        }
        return rows;
    }

    // A labelled field with the suggestion's evidence line under its control.
    private Node field(final MessageKey label, final Node control, final BriefField field) {
        return BriefCards.field(messages, label, withEvidence(control, field));
    }

    private Node withEvidence(final Node control, final BriefField field) {
        return new VBox(
                FIELD_SPACING,
                control,
                Objects.requireNonNull(evidence.get(field), "row").node());
    }

    // A field shows the suggestion's evidence only while it holds what the suggestion wrote into it.
    private void showEvidence(final @Nullable BookBrief brief) {
        if (brief == null) {
            Visibility.show(genderSuggested, false);
        } else {
            showGenderSuggested(brief);
        }
        evidence.forEach((field, row) -> row.show(
                brief == null
                        ? null
                        : viewModel.styleSuggestion().evidenceFor(field, brief).orElse(null)));
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
    // While a translation is at work the model is busy with it, so the suggestion waits and says why.
    private static Node suggestion(
            final BookBriefViewModel viewModel, final Messages messages, final ObservableValue<RunStateView> runRow) {
        if (!viewModel.styleSuggestion().isOffered()) {
            return new VBox();
        }
        final Button suggest = new Button(messages.get(MessageKey.BRIEF_SUGGEST));
        suggest.setId("brief-suggest-style");
        suggest.getStyleClass().add("btn-secondary");
        final BooleanBinding atWork =
                Bindings.createBooleanBinding(() -> runRow.getValue().isAtWork(), runRow);
        Tips.installLive(
                suggest,
                Bindings.createStringBinding(
                        () -> messages.get(
                                atWork.get() ? MessageKey.MODEL_BLOCKED_PAUSE_FIRST : MessageKey.BRIEF_SUGGEST_TIP),
                        atWork));
        suggest.disableProperty().bind(viewModel.styleSuggestion().suggesting().or(atWork));
        suggest.setOnAction(event -> viewModel.styleSuggestion().ask());
        final Label outcome = new Label();
        outcome.setId("brief-suggest-outcome");
        outcome.getStyleClass().add("hint");
        outcome.setWrapText(true);
        outcome.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> outcomeText(viewModel.styleSuggestion().outcome().get(), messages),
                        viewModel.styleSuggestion().outcome()));
        Visibility.bind(outcome, outcome.textProperty().isNotEmpty());
        return new VBox(FIELD_SPACING, suggest, blockedReason(atWork, messages), outcome);
    }

    // A disabled button shows no tooltip, so the reason it is off is a visible line under it.
    private static Label blockedReason(final BooleanBinding atWork, final Messages messages) {
        final Label reason = new Label();
        reason.setId("brief-suggest-reason");
        reason.getStyleClass().add("hint");
        reason.setWrapText(true);
        reason.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> atWork.get() ? messages.get(MessageKey.MODEL_BLOCKED_PAUSE_FIRST) : "", atWork));
        Visibility.bind(reason, atWork);
        return reason;
    }

    static String outcomeText(final StyleSuggestionOutcome outcome, final Messages messages) {
        return switch (outcome.kind()) {
            case NONE -> "";
            case DONE -> messages.get(MessageKey.BRIEF_SUGGEST_DONE);
            case STOPPED -> messages.get(MessageKey.BRIEF_SUGGEST_STOPPED);
            case NO_MODEL -> messages.get(MessageKey.BRIEF_SUGGEST_NO_MODEL);
            case BUSY -> messages.get(MessageKey.ACTIVITY_BLOCKED, messages.get(ActivityKind.SUGGEST_STYLE.label()));
            case FAILED -> {
                final AppError failure = Objects.requireNonNull(outcome.failure(), "failure");
                yield ErrorWords.message(messages, failure);
            }
        };
    }

    private static Label narratorNotice(final BookBriefViewModel viewModel, final Messages messages) {
        final Label notice = BriefCards.hint(messages, MessageKey.BRIEF_NARRATOR_NOTICE);
        notice.setId("brief-tone-narrator-notice");
        Tips.install(messages, notice, MessageKey.BRIEF_NARRATOR_NOTICE_TIP);
        Visibility.bind(notice, viewModel.narratorNotice());
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
        TextLimit.of(voice, VOICE_MAX);
        Tips.install(messages, voice, MessageKey.BRIEF_TONE_VOICE_TIP);
        voice.getStyleClass().add("brief-input");
        voice.setPromptText(messages.get(MessageKey.BRIEF_TONE_VOICE_PROMPT));
        voice.setPrefRowCount(VOICE_ROWS);
        voice.setWrapText(true);
        voice.textProperty().addListener((observed, was, now) -> typed(now, viewModel::setVoiceEra));
        audience.setId("brief-tone-audience");
        TextLimit.of(audience, AUDIENCE_MAX);
        Tips.install(messages, audience, MessageKey.BRIEF_TONE_AUDIENCE_TIP);
        audience.setPromptText(messages.get(MessageKey.BRIEF_TONE_AUDIENCE_PROMPT));
        audience.textProperty().addListener((observed, was, now) -> typed(now, viewModel::setAudience));
    }

    private SearchableCombo<String> genreBox(final BookBriefViewModel viewModel) {
        final SearchableCombo<String> box = SearchableCombo.freeText(
                Arrays.stream(Genre.values()).map(this::genreName).toList(), Function.identity(), Function.identity());
        box.setId("brief-tone-genre");
        TextLimit.of(box.getEditor(), GENRE_MAX);
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
            showEvidence(brief);
        } finally {
            applying = false;
        }
    }

    private String genreName(final Genre genre) {
        return messages.get(GenreLabels.keyOf(genre));
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
