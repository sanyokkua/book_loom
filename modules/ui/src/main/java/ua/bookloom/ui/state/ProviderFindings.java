package ua.bookloom.ui.state;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.ProviderDraft.Field;
import ua.bookloom.ui.state.ProviderDraft.FormFinding;

/**
 * How the Providers form words what it finds: a shared provider rule turned into a finding on its field, and a typed
 * timeout judged as whole seconds from one to {@link ProviderConfigRules#MAX_TIMEOUT}. Kept apart from
 * {@link ProviderDraft} so that record stays a readable size.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProviderFindings {

    private static final long MAX_SECONDS = ProviderConfigRules.MAX_TIMEOUT.toSeconds();

    /** The typed seconds, or -1 when the text is not a whole number. */
    static long parseSeconds(final String text) {
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Whether the typed seconds are a whole number from one second to an hour. */
    static boolean isUsableSeconds(final String text) {
        final long seconds = parseSeconds(text);
        return seconds > 0 && seconds <= MAX_SECONDS;
    }

    /** What is wrong with a typed timeout, if anything: not a whole number above zero, or longer than an hour. */
    static Optional<FormFinding> timeout(final String text, final Field field) {
        final long seconds = parseSeconds(text);
        if (seconds <= 0) {
            return Optional.of(blocking(MessageKey.SETTINGS_ERROR_NUMBER, field));
        }
        return seconds > MAX_SECONDS
                ? Optional.of(blocking(MessageKey.SETTINGS_ERROR_TIMEOUT_TOO_LONG, field))
                : Optional.empty();
    }

    static FormFinding mapped(final ProviderConfigRules.Finding finding) {
        final String subject = finding.subject();
        final List<String> args = subject == null ? List.of() : List.of(subject.strip());
        return switch (finding.rule()) {
            case BASE_URL_INVALID -> blocking(MessageKey.SETTINGS_ERROR_ADDRESS, Field.ADDRESS);
            case BASE_URL_CREDENTIALS -> blocking(MessageKey.SETTINGS_ERROR_ADDRESS_CREDENTIALS, Field.ADDRESS);
            case BASE_URL_PORT -> blocking(MessageKey.SETTINGS_ERROR_ADDRESS_PORT, Field.ADDRESS);
            case TIMEOUT_NOT_POSITIVE -> blocking(MessageKey.SETTINGS_ERROR_NUMBER, Field.REQUEST_TIMEOUT);
            case TIMEOUT_TOO_LONG -> blocking(MessageKey.SETTINGS_ERROR_TIMEOUT_TOO_LONG, Field.REQUEST_TIMEOUT);
            case OLLAMA_CHAT_PATH -> blocking(MessageKey.SETTINGS_ERROR_OLLAMA_CHAT_PATH, Field.CHAT_PATH);
            case CREDENTIAL_HEADER_LITERAL ->
                new FormFinding(MessageKey.SETTINGS_ERROR_HEADER_LITERAL, args, Field.HEADERS, true);
            case HEADER_NAME_INVALID ->
                new FormFinding(MessageKey.SETTINGS_ERROR_HEADER_NAME_INVALID, args, Field.HEADERS, true);
            case CREDENTIAL_REF_NOT_A_NAME -> blocking(MessageKey.SETTINGS_ERROR_REF_NOT_NAME, Field.KEY_VARIABLE);
            case CREDENTIAL_REF_REFERENCE ->
                new FormFinding(MessageKey.SETTINGS_ERROR_REF_REFERENCE, args, Field.KEY_VARIABLE, true);
            case CREDENTIAL_REF_MISSING -> blocking(MessageKey.SETTINGS_ERROR_REF_MISSING, Field.KEY_VARIABLE);
            case API_KEY_HEADER_MISSING -> blocking(MessageKey.SETTINGS_ERROR_KEY_HEADER_MISSING, Field.KEY_HEADER);
            case API_KEY_HEADER_INVALID ->
                new FormFinding(MessageKey.SETTINGS_ERROR_HEADER_NAME_INVALID, args, Field.KEY_HEADER, true);
            case V1_SUFFIX_MISSING ->
                new FormFinding(MessageKey.SETTINGS_FORM_ADDRESS_V1_WARNING, List.of(), Field.ADDRESS, false);
        };
    }

    static FormFinding blocking(final MessageKey key, final Field field) {
        return new FormFinding(key, List.of(), field, true);
    }
}
