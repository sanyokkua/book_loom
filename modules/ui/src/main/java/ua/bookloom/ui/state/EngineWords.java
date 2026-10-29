package ua.bookloom.ui.state;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The words the Providers form shows about the program behind an address: the detected-engine line, an engine's name,
 * and the verdict lines of the Advanced features check, which the verifier writes as machine words.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EngineWords {

    // The thinking check names the field that worked, or only an outcome when none did.
    private static final Set<String> OUTCOMES = Set.of("honoured", "ignored", "refused");

    /**
     * The line under the address.
     *
     * @param messages the catalogue the words come from
     * @param kind how the provider is reached
     * @param engine what is known of the server
     * @return "Detected: …" for a recognised engine, else "Not recognised: generic OpenAI-compatible server"
     */
    public static String line(final Messages messages, final ProviderKind kind, final DraftEngine engine) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(engine, "engine");
        if (engine.detected() == ServerEngine.GENERIC) {
            return messages.get(MessageKey.SETTINGS_ENGINE_UNKNOWN);
        }
        final String name = name(messages, engine.detected());
        final String connection = messages.code(MessageKey.SETTINGS_ENGINE_CONNECTION, kind.name());
        final String version = engine.version();
        return version == null || version.isBlank()
                ? messages.get(MessageKey.SETTINGS_ENGINE_DETECTED, name, connection)
                : messages.get(MessageKey.SETTINGS_ENGINE_DETECTED_VERSION, name, version, connection);
    }

    /**
     * An engine's name in the display language.
     *
     * @param messages the catalogue the words come from
     * @param engine the engine
     * @return the product's own spelling, or the words for a generic server
     */
    public static String name(final Messages messages, final ServerEngine engine) {
        return messages.code(MessageKey.SETTINGS_ENGINE_NAME, engine.name());
    }

    /**
     * One line per check the Advanced features stage ran, as "Structured answers: honoured".
     *
     * @param messages the catalogue the words come from
     * @param note the stage's note in {@link AdvancedCheck}'s machine words, or {@code null}
     * @return the lines in check order; never null, empty when the note names no check
     */
    public static List<String> verdictLines(final Messages messages, final @Nullable String note) {
        Objects.requireNonNull(messages, "messages");
        return AdvancedCheck.read(note).entrySet().stream()
                .map(found -> verdictLine(messages, found))
                .toList();
    }

    private static String verdictLine(final Messages messages, final Map.Entry<AdvancedCheck, String> found) {
        final AdvancedCheck check = found.getKey();
        return messages.get(
                MessageKey.SETTINGS_ADVANCED_LINE,
                messages.code(MessageKey.SETTINGS_ADVANCED_CHECK, check.token()),
                valueWords(messages, check, found.getValue()));
    }

    /**
     * What one check found, in the display language.
     *
     * @param messages the catalogue the words come from
     * @param check the check
     * @param value its value in the note
     * @return an outcome word, "works with {field}", or the field that caps the answer
     */
    public static String valueWords(final Messages messages, final AdvancedCheck check, final String value) {
        final boolean outcome = OUTCOMES.contains(value.toLowerCase(Locale.ROOT));
        return switch (check) {
            case SCHEMA, SAMPLERS -> messages.code(MessageKey.SETTINGS_ADVANCED_OUTCOME, value);
            case THINKING ->
                outcome
                        ? messages.get(MessageKey.SETTINGS_ADVANCED_NO_SWITCH)
                        : messages.get(MessageKey.SETTINGS_ADVANCED_SWITCH, value);
            case CAP ->
                AdvancedCheck.NO_FIELD.equals(value) ? messages.get(MessageKey.SETTINGS_ADVANCED_NO_CAP) : value;
        };
    }
}
