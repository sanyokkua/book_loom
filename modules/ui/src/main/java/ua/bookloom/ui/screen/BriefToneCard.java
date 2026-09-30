package ua.bookloom.ui.screen;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.Genre;

/**
 * The Tone &amp; style card: a genre box that suggests the forty predefined genres and also takes free text, the
 * register, and the free-text narrative voice and audience.
 *
 * <p>The person sees a predefined genre in the interface language while the brief holds its English name, because the
 * prompt is written in English.
 */
@Slf4j
final class BriefToneCard {

    private static final int VOICE_ROWS = 2;

    private final Messages messages;
    private final SearchableCombo<String> genre;
    private final BriefChoice<Register> register;
    private final TextArea voice = new TextArea();
    private final TextField audience = new TextField();
    private final Node node;
    // True while a value from the view model is written into a control, so it is not handed back as a person's choice.
    private boolean applying;

    BriefToneCard(final BookBriefViewModel viewModel, final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.genre = genreBox(viewModel);
        this.register = new BriefChoice<>(
                "brief-tone-register",
                messages,
                List.of(
                        new BriefChoice.Option<>(Register.FORMAL_LITERARY, MessageKey.BRIEF_REGISTER_FORMAL),
                        new BriefChoice.Option<>(Register.NEUTRAL, MessageKey.BRIEF_REGISTER_NEUTRAL),
                        new BriefChoice.Option<>(Register.CASUAL, MessageKey.BRIEF_REGISTER_CASUAL)),
                viewModel::setRegister);
        voice.setId("brief-tone-voice");
        voice.getStyleClass().add("brief-input");
        voice.setPromptText(messages.get(MessageKey.BRIEF_TONE_VOICE_PROMPT));
        voice.setPrefRowCount(VOICE_ROWS);
        voice.setWrapText(true);
        voice.textProperty().addListener((observed, was, now) -> typed(now, viewModel::setVoiceEra));
        audience.setId("brief-tone-audience");
        audience.setPromptText(messages.get(MessageKey.BRIEF_TONE_AUDIENCE_PROMPT));
        audience.textProperty().addListener((observed, was, now) -> typed(now, viewModel::setAudience));
        this.node = BriefCards.card(
                "brief-tone-card",
                messages,
                MessageKey.BRIEF_CARD_TONE,
                BriefCards.field(messages, MessageKey.BRIEF_TONE_GENRE, genre),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_REGISTER, register.node()),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_VOICE, voice),
                BriefCards.field(messages, MessageKey.BRIEF_TONE_AUDIENCE, audience));
    }

    private SearchableCombo<String> genreBox(final BookBriefViewModel viewModel) {
        final SearchableCombo<String> box = SearchableCombo.freeText(
                Arrays.stream(Genre.values()).map(this::genreName).toList(), Function.identity(), Function.identity());
        box.setId("brief-tone-genre");
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
