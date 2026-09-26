package ua.bookloom.ui.screen;

import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The display name of a provider, which its configuration does not carry: the registry holds an id and an endpoint,
 * and a name is a translatable word.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below
// (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProviderNames {

    private static final Map<String, MessageKey> NAMES =
            Map.of("ollama", MessageKey.SETTINGS_PROVIDER_OLLAMA, "lmstudio", MessageKey.SETTINGS_PROVIDER_LMSTUDIO);

    /**
     * Names a provider for display.
     *
     * @param messages the catalogue to name it from
     * @param id a provider id
     * @return the catalogue name for a known provider, otherwise the id itself, so a provider added later still
     *     shows something recognisable
     */
    static String displayName(final Messages messages, final String id) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(id, "id");
        final MessageKey key = NAMES.get(id);
        return key == null ? id : messages.get(key);
    }
}
