package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What Settings → Providers tells the person about the model they chose and the server it runs on, from the model's
 * name and the provider's kind alone: a pure function, so it never calls a server and is the same on every screen.
 */
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModelAdvice {

    private record Rule(String needle, Note note) {}

    private static final List<Rule> MODEL_RULES = List.of(
            new Rule("gemma426b", new Note(Level.RECOMMENDED, MessageKey.SETTINGS_ADVICE_GEMMA_26B)),
            new Rule("gemma4e4b", new Note(Level.MINIMUM, MessageKey.SETTINGS_ADVICE_GEMMA_E4B)),
            new Rule("gemma4e2b", new Note(Level.WARNING, MessageKey.SETTINGS_ADVICE_GEMMA_E2B)),
            new Rule("gptoss", new Note(Level.WARNING, MessageKey.SETTINGS_ADVICE_GPT_OSS)));

    /** How strongly a note speaks. */
    public enum Level {
        /** The model the translation quality was measured with. */
        RECOMMENDED,
        /** The smallest model that translates a book acceptably. */
        MINIMUM,
        /** A model that translates a book poorly or slowly. */
        WARNING,
        /** A fact about the server, neither good nor bad. */
        INFO
    }

    /**
     * One line of advice.
     *
     * @param level how strongly it speaks
     * @param text the catalogue key of its wording
     */
    public record Note(Level level, MessageKey text) {}

    /**
     * The notes for a chosen model on a provider, model notes first, then the server's.
     *
     * @param kind the selected provider's kind
     * @param modelId the model text as chosen, any letter case; blank for none
     * @return the notes in the order they are shown; never null, empty for an unknown model on a server with no note
     */
    public static List<Note> notesFor(final ProviderKind kind, final String modelId) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(modelId, "modelId");
        final String compact = compact(modelId);
        final List<Note> notes = new ArrayList<>();
        MODEL_RULES.stream()
                .filter(rule -> compact.contains(rule.needle()))
                .findFirst()
                .ifPresent(rule -> notes.add(rule.note()));
        if (kind == ProviderKind.OPENAI_COMPATIBLE) {
            notes.add(new Note(Level.INFO, MessageKey.SETTINGS_ADVICE_LM_STUDIO));
        }
        log.debug("advice for {} model '{}': {} note(s)", kind, modelId, notes.size());
        return List.copyOf(notes);
    }

    // Ollama writes gemma4:26b-mlx where LM Studio writes google/gemma-4-26b, so only letters and digits are compared.
    private static String compact(final String modelId) {
        final StringBuilder letters = new StringBuilder();
        modelId.toLowerCase(Locale.ROOT)
                .codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(letters::appendCodePoint);
        return letters.toString();
    }
}
