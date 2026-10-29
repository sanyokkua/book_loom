package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.EngineWords;
import ua.bookloom.ui.state.ProviderEngine;
import ua.bookloom.ui.state.ProviderForm;

/**
 * "Treat this server as…": the engines a person can name for a server, and "As detected" to follow detection. The
 * row is hidden for an app that cannot translate a book, since naming an engine cannot make it usable.
 */
final class ProviderTreatAsField {

    /** One entry of the choice; {@code engine} is null for "As detected". */
    record Option(@Nullable ServerEngine engine) {}

    private static final List<ServerEngine> ORDER = List.of(
            ServerEngine.OLLAMA,
            ServerEngine.LM_STUDIO,
            ServerEngine.LLAMA_CPP,
            ServerEngine.UNSLOTH,
            ServerEngine.OPENROUTER,
            ServerEngine.GENERIC);

    private final ProviderForm form;
    private final ComboBox<Option> choice = new ComboBox<>();
    private final InvalidationListener onDraft = observed -> show();

    // Set while the combo follows the draft, so the echo is not taken for the person's choice.
    private boolean showing;

    ProviderTreatAsField(final ProviderForm form, final ProviderEngine engine, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(messages, "messages");
        choice.setId("settings-form-treat-as");
        choice.getStyleClass().add("w-name");
        choice.getItems()
                .setAll(Stream.concat(
                                Stream.of(new Option(null)), ORDER.stream().map(Option::new))
                        .toList());
        choice.setConverter(converter(messages));
        Tips.install(messages, choice, MessageKey.SETTINGS_TREAT_AS_TIP);
        choice.valueProperty().addListener((observed, was, now) -> {
            if (!showing && now != null) {
                engine.treatAs(now.engine());
            }
        });
        form.draft().addListener(new WeakInvalidationListener(onDraft));
        show();
    }

    private static StringConverter<Option> converter(final Messages messages) {
        return new StringConverter<>() {
            @Override
            public String toString(final @Nullable Option option) {
                if (option == null) {
                    return "";
                }
                final ServerEngine named = option.engine();
                return named == null
                        ? messages.get(MessageKey.SETTINGS_TREAT_AS_DETECTED)
                        : EngineWords.name(messages, named);
            }

            @Override
            public Option fromString(final String text) {
                throw new UnsupportedOperationException("the engine is picked, never typed");
            }
        };
    }

    ComboBox<Option> node() {
        return choice;
    }

    private void show() {
        final ServerEngine chosen = form.draft().get().engine().choice();
        showing = true;
        try {
            choice.setValue(new Option(chosen));
        } finally {
            showing = false;
        }
    }
}
