package ua.bookloom.api.llm;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/**
 * The semantic rules for a provider description, kept pure so the registry, the form and the command line agree. An
 * interface only because {@code ..api..} may hold nothing but records, enums and interfaces.
 */
public interface ProviderConfigRules {

    /** The shape a credential-like header value must have: a reference to an environment variable, never a secret. */
    Pattern ENV_REFERENCE = Pattern.compile("\\$\\{env:[A-Za-z_][A-Za-z0-9_]*}");

    /**
     * The shape a credential reference must have: the name of an environment variable. A value such as a key pasted
     * into the field does not have it, so it is refused before it could be stored.
     */
    Pattern ENV_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** A path ending in the {@code v1} segment. */
    Pattern V1_SUFFIX = Pattern.compile("(^|/)v1/?$");

    /** Lower-case fragments that make a header name credential-like. */
    List<String> CREDENTIAL_NAME_PARTS = List.of("authorization", "key", "token", "secret");

    /**
     * What the rules found.
     *
     * @param errors the refusals; empty when the description may be saved
     * @param warnings short advice that does not stop a save
     */
    record Check(List<AppError> errors, List<String> warnings) {

        /** Copies both lists. */
        public Check {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }

        /**
         * Tells whether the description may be saved.
         *
         * @return {@code true} if there is no refusal, {@code false} otherwise
         */
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Checks a description without throwing. No value of a refused header is echoed in a message.
     *
     * @param config the description to check
     * @return the refusals and warnings; never null
     */
    static Check check(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final List<AppError> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        checkEndpoint(config, errors, warnings);
        checkTimeouts(config, errors);
        checkOverrides(config, errors);
        checkHeaders(config.headers(), errors);
        checkSignIn(config, errors);
        return new Check(errors, warnings);
    }

    private static void checkEndpoint(
            final ProviderConfig config, final List<AppError> errors, final List<String> warnings) {
        final URI url = config.baseUrl();
        final String scheme = url.getScheme();
        final boolean http = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        if (!url.isAbsolute() || !http || url.getHost() == null) {
            errors.add(validation("The provider base URL must be an absolute HTTP or HTTPS URL."));
            return;
        }
        final String path = url.getPath() == null ? "" : url.getPath();
        if (config.kind() == ProviderKind.OPENAI_COMPATIBLE
                && !V1_SUFFIX.matcher(path).find()) {
            warnings.add("An OpenAI-compatible address usually ends with /v1.");
        }
    }

    private static void checkTimeouts(final ProviderConfig config, final List<AppError> errors) {
        if (!isPositive(config.connectTimeout()) || !isPositive(config.requestTimeout())) {
            errors.add(validation("Provider connect and request timeouts must both be positive."));
        }
    }

    private static boolean isPositive(final Duration value) {
        return !value.isZero() && !value.isNegative();
    }

    private static void checkOverrides(final ProviderConfig config, final List<AppError> errors) {
        if (config.kind() == ProviderKind.OLLAMA && config.chatPath() != null) {
            errors.add(validation("An Ollama provider cannot override the chat path."));
        }
    }

    private static void checkHeaders(final Map<String, String> headers, final List<AppError> errors) {
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            final String name = header.getKey().toLowerCase(Locale.ROOT);
            final boolean credentialLike = CREDENTIAL_NAME_PARTS.stream().anyMatch(name::contains);
            if (credentialLike && !ENV_REFERENCE.matcher(header.getValue()).matches()) {
                errors.add(validation("The header " + header.getKey()
                        + " looks like a credential; give it as ${env:NAME}, the name of an environment variable."));
            }
        }
    }

    private static void checkSignIn(final ProviderConfig config, final List<AppError> errors) {
        final String reference = config.credentialRef();
        if (reference != null
                && !reference.isBlank()
                && !ENV_NAME.matcher(reference).matches()) {
            errors.add(validation("The sign-in reference must be the name of an environment variable (letters, digits"
                    + " and underscores, not starting with a digit), never the key itself."));
            return;
        }
        if (config.auth() == ProviderAuth.NONE) {
            return;
        }
        if (isBlank(config.credentialRef())) {
            errors.add(missing("Sign-in needs the name of the environment variable that holds the key."));
        }
        if (config.auth() == ProviderAuth.API_KEY && isBlank(config.apiKeyHeader())) {
            errors.add(missing("An API-key sign-in needs the header name that carries the key."));
        }
    }

    private static boolean isBlank(final @Nullable String value) {
        return value == null || value.isBlank();
    }

    private static AppError validation(final String message) {
        return AppError.of(ErrorCode.validation, "Invalid provider configuration", message);
    }

    private static AppError missing(final String message) {
        return AppError.of(ErrorCode.missingCredential, "Provider sign-in incomplete", message);
    }
}
