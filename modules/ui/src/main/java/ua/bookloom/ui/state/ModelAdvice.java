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
 * name and the provider's server alone: a pure function, so it never calls a server and is the same on every screen.
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

    private static final String LM_STUDIO_PORT = ":1234";
    private static final String LM_STUDIO_WORD = "lmstudio";

    /** The server a provider row points at, as far as its notes go. */
    public enum Server {
        /** Ollama's native API. */
        OLLAMA,
        /** LM Studio's OpenAI-compatible server: slower here, unstreamed, more control codes. */
        LM_STUDIO,
        /** Any other OpenAI-compatible server, about which nothing was measured. */
        OTHER_OPENAI_COMPATIBLE;

        /**
         * Tells the server from a provider row.
         *
         * @param row the provider; LM Studio is an OpenAI-compatible one on LM Studio's port 1234 or named so
         * @return the server; never null
         */
        public static Server of(final ProviderRow row) {
            Objects.requireNonNull(row, "row");
            if (row.kind() == ProviderKind.OLLAMA) {
                return OLLAMA;
            }
            final boolean lmStudio = row.hostPort().endsWith(LM_STUDIO_PORT)
                    || compact(row.id()).contains(LM_STUDIO_WORD)
                    || compact(row.displayName()).contains(LM_STUDIO_WORD);
            log.debug("provider '{}' at {} is LM Studio: {}", row.id(), row.hostPort(), lmStudio);
            return lmStudio ? LM_STUDIO : OTHER_OPENAI_COMPATIBLE;
        }
    }

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
     * @param server the selected provider's server
     * @param modelId the model text as chosen, any letter case; blank for none
     * @return the notes in the order they are shown; never null, empty for an unknown model on a server with no note
     */
    public static List<Note> notesFor(final Server server, final String modelId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(modelId, "modelId");
        final String compact = compact(modelId);
        final List<Note> notes = new ArrayList<>();
        MODEL_RULES.stream()
                .filter(rule -> compact.contains(rule.needle()))
                .findFirst()
                .ifPresent(rule -> notes.add(rule.note()));
        // Only LM Studio was measured; a generic OpenAI-compatible server gets no claim about its speed.
        if (server == Server.LM_STUDIO) {
            notes.add(new Note(Level.INFO, MessageKey.SETTINGS_ADVICE_LM_STUDIO));
        }
        log.debug("advice for {} model '{}': {} note(s)", server, modelId, notes.size());
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
