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

    /**
     * The characters an HTTP header name may hold (RFC 7230's {@code token}); a space, a colon or a control character
     * would pass a save and then fail every request as a transport error.
     */
    Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    /** The longest connect or request timeout: an hour, longer than any single model call is worth waiting for. */
    Duration MAX_TIMEOUT = Duration.ofHours(1);

    /** A path ending in the {@code v1} segment. */
    Pattern V1_SUFFIX = Pattern.compile("(^|/)v1/?$");

    /** Lower-case fragments that make a header name credential-like. */
    List<String> CREDENTIAL_NAME_PARTS = List.of("authorization", "key", "token", "secret");

    /** Which rule a finding came from, so a screen can word it in the person's language. */
    enum Rule {
        /** The base URL is not an absolute HTTP or HTTPS address. */
        BASE_URL_INVALID,
        /** A connect or request timeout is zero or negative. */
        TIMEOUT_NOT_POSITIVE,
        /** A connect or request timeout is longer than {@link #MAX_TIMEOUT}. */
        TIMEOUT_TOO_LONG,
        /** A custom header's name holds a character a header name may not. */
        HEADER_NAME_INVALID,
        /** The API-key header's name holds a character a header name may not. */
        API_KEY_HEADER_INVALID,
        /** An Ollama provider overrides the chat path. */
        OLLAMA_CHAT_PATH,
        /** A credential-like header carries a literal value instead of {@code ${env:NAME}}. */
        CREDENTIAL_HEADER_LITERAL,
        /** The sign-in reference is not an environment variable name. */
        CREDENTIAL_REF_NOT_A_NAME,
        /** Sign-in is on but no environment variable name is given. */
        CREDENTIAL_REF_MISSING,
        /** An API-key sign-in names no header. */
        API_KEY_HEADER_MISSING,
        /** An OpenAI-compatible address does not end in {@code /v1} (advice only). */
        V1_SUFFIX_MISSING
    }

    /**
     * One thing a rule found, kept apart from the English text of {@link Check#errors()}.
     *
     * @param rule the rule that fired
     * @param subject the header name the finding is about, or null; never a value
     */
    record Finding(Rule rule, @Nullable String subject) {

        /** Rejects a missing rule. */
        public Finding {
            Objects.requireNonNull(rule, "rule");
        }
    }

    /**
     * What the rules found.
     *
     * @param errors the refusals; empty when the description may be saved
     * @param warnings short advice that does not stop a save
     * @param findings the same refusals and advice as rules, in the order found
     */
    record Check(List<AppError> errors, List<String> warnings, List<Finding> findings) {

        /** Copies the lists. */
        public Check {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
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
        final List<Finding> findings = new ArrayList<>();
        checkEndpoint(config, errors, warnings, findings);
        checkTimeouts(config, errors, findings);
        checkOverrides(config, errors, findings);
        checkHeaders(config.headers(), errors, findings);
        checkSignIn(config, errors, findings);
        return new Check(errors, warnings, findings);
    }

    private static void checkEndpoint(
            final ProviderConfig config,
            final List<AppError> errors,
            final List<String> warnings,
            final List<Finding> findings) {
        final URI url = config.baseUrl();
        final String scheme = url.getScheme();
        final boolean http = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        if (!url.isAbsolute() || !http || url.getHost() == null) {
            errors.add(validation("The provider base URL must be an absolute HTTP or HTTPS URL."));
            findings.add(new Finding(Rule.BASE_URL_INVALID, null));
            return;
        }
        final String path = url.getPath() == null ? "" : url.getPath();
        if (config.kind() == ProviderKind.OPENAI_COMPATIBLE
                && !V1_SUFFIX.matcher(path).find()) {
            warnings.add("An OpenAI-compatible address usually ends with /v1.");
            findings.add(new Finding(Rule.V1_SUFFIX_MISSING, null));
        }
    }

    private static void checkTimeouts(
            final ProviderConfig config, final List<AppError> errors, final List<Finding> findings) {
        if (!isPositive(config.connectTimeout()) || !isPositive(config.requestTimeout())) {
            errors.add(validation("Provider connect and request timeouts must both be positive."));
            findings.add(new Finding(Rule.TIMEOUT_NOT_POSITIVE, null));
        } else if (config.connectTimeout().compareTo(MAX_TIMEOUT) > 0
                || config.requestTimeout().compareTo(MAX_TIMEOUT) > 0) {
            errors.add(validation("Provider connect and request timeouts must be at most one hour."));
            findings.add(new Finding(Rule.TIMEOUT_TOO_LONG, null));
        }
    }

    private static boolean isPositive(final Duration value) {
        return !value.isZero() && !value.isNegative();
    }

    private static void checkOverrides(
            final ProviderConfig config, final List<AppError> errors, final List<Finding> findings) {
        if (config.kind() == ProviderKind.OLLAMA && config.chatPath() != null) {
            errors.add(validation("An Ollama provider cannot override the chat path."));
            findings.add(new Finding(Rule.OLLAMA_CHAT_PATH, null));
        }
    }

    private static void checkHeaders(
            final Map<String, String> headers, final List<AppError> errors, final List<Finding> findings) {
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            if (!HEADER_NAME.matcher(header.getKey()).matches()) {
                errors.add(validation("The header name " + header.getKey().strip()
                        + " holds a space, a colon or another character a header name may not."));
                findings.add(new Finding(Rule.HEADER_NAME_INVALID, header.getKey()));
                continue;
            }
            final String name = header.getKey().toLowerCase(Locale.ROOT);
            final boolean credentialLike = CREDENTIAL_NAME_PARTS.stream().anyMatch(name::contains);
            if (credentialLike && !ENV_REFERENCE.matcher(header.getValue()).matches()) {
                errors.add(validation("The header " + header.getKey()
                        + " looks like a credential; give it as ${env:NAME}, the name of an environment variable."));
                findings.add(new Finding(Rule.CREDENTIAL_HEADER_LITERAL, header.getKey()));
            }
        }
    }

    private static void checkSignIn(
            final ProviderConfig config, final List<AppError> errors, final List<Finding> findings) {
        final String reference = config.credentialRef();
        if (reference != null
                && !reference.isBlank()
                && !ENV_NAME.matcher(reference).matches()) {
            errors.add(validation("The sign-in reference must be the name of an environment variable (letters, digits"
                    + " and underscores, not starting with a digit), never the key itself."));
            findings.add(new Finding(Rule.CREDENTIAL_REF_NOT_A_NAME, null));
            return;
        }
        if (config.auth() == ProviderAuth.NONE) {
            return;
        }
        if (isBlank(config.credentialRef())) {
            errors.add(missing("Sign-in needs the name of the environment variable that holds the key."));
            findings.add(new Finding(Rule.CREDENTIAL_REF_MISSING, null));
        }
        final String keyHeader = config.apiKeyHeader();
        if (config.auth() == ProviderAuth.API_KEY && isBlank(keyHeader)) {
            errors.add(missing("An API-key sign-in needs the header name that carries the key."));
            findings.add(new Finding(Rule.API_KEY_HEADER_MISSING, null));
        } else if (config.auth() == ProviderAuth.API_KEY
                && keyHeader != null
                && !HEADER_NAME.matcher(keyHeader).matches()) {
            errors.add(validation("The key header name " + keyHeader.strip()
                    + " holds a space, a colon or another character a header name may not."));
            findings.add(new Finding(Rule.API_KEY_HEADER_INVALID, keyHeader));
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
