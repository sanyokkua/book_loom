package ua.bookloom.llm.http;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigRules;

/**
 * Builds the headers of one request from a provider description: the JSON headers, the custom headers with their
 * {@code ${env:NAME}} references resolved, and the sign-in header last so it always wins. Resolving happens before
 * anything is sent, so a missing variable refuses the call instead of sending it unsigned. A resolved value is never
 * logged or placed in an error; only variable and header names are.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestHeaders {

    /** What a masked header value shows in place of the secret. */
    public static final String MASK = "••••";

    private static final int DELETE = 0x7f;
    private static final String AUTHORIZATION = "Authorization";
    private static final Pattern ENV_REFERENCE = Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Set<String> RESTRICTED =
            Set.of("host", "content-length", "connection", "expect", "upgrade", "transfer-encoding");

    /**
     * Resolves the headers a request to this provider carries.
     *
     * @param config the provider description
     * @param env looks up an environment variable; a null or blank answer counts as unset
     * @return the headers in send order, or {@code missingCredential} naming the unset variable, or
     *     {@code validation} naming a header whose name is restricted or not a valid header name, or whose value holds
     *     a control character
     */
    public static Result<Map<String, String>> resolve(ProviderConfig config, Function<String, @Nullable String> env) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(env, "env");
        log.debug(
                "Resolving request headers provider={} auth={} customHeaderNames={}",
                config.id(),
                config.auth(),
                config.headers().keySet());
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        for (final Map.Entry<String, String> custom : config.headers().entrySet()) {
            final Result<String> value = customValue(custom.getKey(), custom.getValue(), env);
            if (value.isErr()) {
                return Result.err(Objects.requireNonNull(value.error(), "error"));
            }
            put(headers, custom.getKey(), Objects.requireNonNull(value.data(), "value"));
        }
        return withSignIn(config, env, headers);
    }

    /**
     * Describes the headers for display with every secret hidden; reads the description only, never the environment.
     *
     * @param config the provider description
     * @return the header lines as the person may see them: the sign-in value and any value that came from, or is named
     *     like, a credential show {@link #MASK}
     */
    public static Map<String, String> masked(ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final Map<String, String> shown = new LinkedHashMap<>();
        shown.put("Content-Type", "application/json");
        shown.put("Accept", "application/json");
        config.headers().forEach((name, value) -> put(shown, name, isSecret(name, value) ? MASK : value));
        switch (config.auth()) {
            case BEARER -> put(shown, AUTHORIZATION, "Bearer " + MASK);
            case API_KEY -> put(shown, headerName(config), MASK);
            case NONE -> log.debug("No sign-in header to show provider={}", config.id());
        }
        return shown;
    }

    private static Result<Map<String, String>> withSignIn(
            ProviderConfig config, Function<String, @Nullable String> env, Map<String, String> headers) {
        if (config.auth() == ProviderAuth.NONE) {
            log.debug("No sign-in header provider={} auth=NONE", config.id());
            return Result.ok(headers);
        }
        final String variable = config.credentialRef() == null ? "" : config.credentialRef();
        final Result<String> key = lookup(variable, env);
        if (key.isErr()) {
            return Result.err(Objects.requireNonNull(key.error(), "error"));
        }
        final String name = config.auth() == ProviderAuth.BEARER ? AUTHORIZATION : headerName(config);
        if (!isHeaderName(name)) {
            return Result.err(validation(ProviderConfigRules.BAD_HEADER_NAME));
        }
        final String value = config.auth() == ProviderAuth.BEARER ? "Bearer " + key.data() : key.data();
        final Result<String> checked = checkedValue(name, Objects.requireNonNull(value, "value"));
        if (checked.isErr()) {
            return Result.err(Objects.requireNonNull(checked.error(), "error"));
        }
        put(headers, name, value);
        return Result.ok(headers);
    }

    private static String headerName(ProviderConfig config) {
        return config.apiKeyHeader() == null ? "" : config.apiKeyHeader();
    }

    private static Result<String> customValue(String name, String template, Function<String, @Nullable String> env) {
        if (!isHeaderName(name)) {
            return Result.err(validation(ProviderConfigRules.BAD_HEADER_NAME));
        }
        if (RESTRICTED.contains(name.toLowerCase(Locale.ROOT))) {
            log.warn("Request header refused because its name is restricted header={}", name);
            return Result.err(validation("The header " + name + " is set by the client and cannot be overridden."));
        }
        final Matcher matcher = ENV_REFERENCE.matcher(template);
        final StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            final Result<String> value = lookup(matcher.group(1), env);
            if (value.isErr()) {
                return value;
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(Objects.requireNonNull(value.data())));
        }
        matcher.appendTail(resolved);
        return checkedValue(name, resolved.toString());
    }

    // A name with a space, a colon or a control character would make the HTTP client throw at send time; a refused
    // name is never logged or named back, since it may be a key pasted into the wrong field.
    private static boolean isHeaderName(String name) {
        final boolean valid = ProviderConfigRules.isHeaderName(name);
        if (!valid) {
            log.warn("Request header refused because its name is not a valid header name length={}", name.length());
        }
        return valid;
    }

    private static Result<String> lookup(String variable, Function<String, @Nullable String> env) {
        final String value = variable.isBlank() ? null : env.apply(variable);
        if (value == null || value.isBlank()) {
            log.warn("Credential variable is not set variable={}", variable);
            return Result.err(AppError.of(
                    ErrorCode.missingCredential,
                    "Sign-in key missing",
                    variable.isBlank()
                            ? "The provider needs the name of the environment variable that holds its key."
                            : missingVariable(variable)));
        }
        return Result.ok(value);
    }

    // An app opened from the Dock, a file manager or an IDE never sees a shell profile's export lines, so the words
    // say where a variable is read from; BookLoom reads it at each request, so no restart is needed.
    private static String missingVariable(String variable) {
        return "The environment variable " + variable + " is not set where BookLoom can read it."
                + " An app opened from the Dock, the Start menu or an IDE does not see export lines in a shell profile."
                + " Set it for your user account — macOS: launchctl setenv " + variable + " <key>;"
                + " Windows: setx " + variable + " <key>; Linux: a line " + variable
                + "=<key> in ~/.config/environment.d/50-bookloom.conf — and test again; no restart is needed.";
    }

    private static Result<String> checkedValue(String name, String value) {
        if (value.chars().anyMatch(c -> (c < ' ' && c != '\t') || c == DELETE)) {
            log.warn("Request header refused because its value has a control character header={}", name);
            return Result.err(
                    validation("The value of the header " + name + " has a line break or control character."));
        }
        return Result.ok(value);
    }

    private static boolean isSecret(String name, String value) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return ENV_REFERENCE.matcher(value).find()
                || ProviderConfigRules.CREDENTIAL_NAME_PARTS.stream().anyMatch(lower::contains);
    }

    private static void put(Map<String, String> headers, String name, String value) {
        headers.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
        headers.put(name, value);
    }

    private static AppError validation(String message) {
        return AppError.of(ErrorCode.validation, "Invalid provider configuration", message);
    }
}
