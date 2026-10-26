package ua.bookloom.ui.state;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.With;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.EnvironmentVariables;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What the Providers form holds while a person types: every field as text, so a half-typed number or address is still
 * a draft and only {@link #toConfig} or {@link #findings} judge it. A blank text means "not given".
 *
 * <p>No key is ever held, only the name of the environment variable that carries it.
 *
 * @param name the name shown in the list
 * @param kind the wire dialect
 * @param auth how requests sign in
 * @param apiKeyHeader the header carrying the key for an API-key sign-in
 * @param credentialRef the environment variable's name
 * @param address the base URL
 * @param modelsPath the model-list path override
 * @param chatPath the chat path override (never for Ollama)
 * @param connectSeconds the connect timeout, whole seconds as typed
 * @param requestSeconds the request timeout, whole seconds as typed
 * @param model the model typed for this provider; blank for none
 * @param headers the extra header rows
 * @param customModels model names added to the listing
 */
@With
public record ProviderDraft(
        String name,
        ProviderKind kind,
        ProviderAuth auth,
        String apiKeyHeader,
        String credentialRef,
        String address,
        String modelsPath,
        String chatPath,
        String connectSeconds,
        String requestSeconds,
        String model,
        List<HeaderRow> headers,
        List<String> customModels) {

    /** The key header an API-key sign-in starts with. */
    public static final String DEFAULT_KEY_HEADER = "X-Api-Key";

    private static final String OLLAMA_ADDRESS = "http://localhost:11434";
    private static final String OPENAI_ADDRESS = "http://localhost:1234/v1";
    private static final String STAND_IN_ADDRESS = "http://localhost";

    /** One extra header as typed; the value of a credential-like header must be {@code ${env:NAME}}. */
    public record HeaderRow(String name, String value) {

        /** Rejects a missing name or value. */
        public HeaderRow {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    /** The form field a finding belongs to, so the screen can mark it. */
    public enum Field {
        /** The provider's name. */
        NAME,
        /** The base URL. */
        ADDRESS,
        /** The connect timeout. */
        CONNECT_TIMEOUT,
        /** The request timeout. */
        REQUEST_TIMEOUT,
        /** The chat path override. */
        CHAT_PATH,
        /** The API-key header's name. */
        KEY_HEADER,
        /** The environment variable's name. */
        KEY_VARIABLE,
        /** The extra header rows. */
        HEADERS
    }

    /** Whether the sign-in's environment variable has a value, without ever showing it. */
    public enum CredentialStatus {
        /** No sign-in, or no variable named yet. */
        NONE,
        /** The variable has a value. */
        SET,
        /** The variable is missing or empty. */
        NOT_SET
    }

    /**
     * One thing the form tells the person.
     *
     * @param key the message
     * @param args its arguments; a header's name, never a value
     * @param field where it belongs
     * @param blocking {@code true} if it stops a save, {@code false} for advice
     */
    public record FormFinding(MessageKey key, List<String> args, Field field, boolean blocking) {

        /** Copies the arguments. */
        public FormFinding {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(field, "field");
            args = List.copyOf(Objects.requireNonNull(args, "args"));
        }
    }

    /** Copies the lists and rejects missing parts. */
    public ProviderDraft {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(apiKeyHeader, "apiKeyHeader");
        Objects.requireNonNull(credentialRef, "credentialRef");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(modelsPath, "modelsPath");
        Objects.requireNonNull(chatPath, "chatPath");
        Objects.requireNonNull(connectSeconds, "connectSeconds");
        Objects.requireNonNull(requestSeconds, "requestSeconds");
        Objects.requireNonNull(model, "model");
        headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
        customModels = List.copyOf(Objects.requireNonNull(customModels, "customModels"));
    }

    /**
     * A new provider of one kind, its address already the usual local one.
     *
     * @param kind the dialect to start from
     * @return the draft with default timeouts and no sign-in
     */
    public static ProviderDraft blank(final ProviderKind kind) {
        Objects.requireNonNull(kind, "kind");
        return new ProviderDraft(
                "",
                kind,
                ProviderAuth.NONE,
                "",
                "",
                presetAddress(kind),
                "",
                "",
                Long.toString(ProviderConfig.DEFAULT_CONNECT_TIMEOUT.toSeconds()),
                Long.toString(ProviderConfig.DEFAULT_REQUEST_TIMEOUT.toSeconds()),
                "",
                List.of(),
                List.of());
    }

    /**
     * The draft of a stored provider.
     *
     * @param config the stored description
     * @param model the model typed for it; blank for none
     * @return the same values as text; the name is the id when the provider has none
     */
    public static ProviderDraft of(final ProviderConfig config, final String model) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(model, "model");
        final List<HeaderRow> rows = config.headers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> new HeaderRow(entry.getKey(), entry.getValue()))
                .toList();
        return new ProviderDraft(
                config.displayName() == null ? config.id() : config.displayName(),
                config.kind(),
                config.auth(),
                orEmpty(config.apiKeyHeader()),
                orEmpty(config.credentialRef()),
                config.baseUrl().toString(),
                orEmpty(config.modelsPath()),
                orEmpty(config.chatPath()),
                Long.toString(config.connectTimeout().toSeconds()),
                Long.toString(config.requestTimeout().toSeconds()),
                model,
                rows,
                config.customModels());
    }

    /**
     * Switches the dialect: the overrides belong to the old one and are cleared, and a preset address follows the
     * kind while an address the person wrote stays.
     *
     * @param newKind the dialect to switch to
     * @return the changed draft
     */
    public ProviderDraft withKind(final ProviderKind newKind) {
        Objects.requireNonNull(newKind, "newKind");
        final boolean preset = address.isBlank() || address.equals(presetAddress(kind));
        return new ProviderDraft(
                name,
                newKind,
                auth,
                apiKeyHeader,
                credentialRef,
                preset ? presetAddress(newKind) : address,
                "",
                "",
                connectSeconds,
                requestSeconds,
                model,
                headers,
                customModels);
    }

    /**
     * The models to offer: those a server listed, then the ones added here, each once.
     *
     * @param offered the names a listing returned
     * @return the names in that order
     */
    public List<String> modelChoices(final List<String> offered) {
        final Set<String> names = new LinkedHashSet<>(offered);
        customModels.stream().map(String::strip).filter(name -> !name.isEmpty()).forEach(names::add);
        return List.copyOf(names);
    }

    /**
     * Changes how requests sign in; an API-key sign-in with no header name yet gets {@value #DEFAULT_KEY_HEADER}, the
     * name most servers that take a key in their own header expect, so the form does not start out refused.
     *
     * @param newAuth the sign-in picked
     * @return the draft with that sign-in
     */
    public ProviderDraft withSignIn(final ProviderAuth newAuth) {
        Objects.requireNonNull(newAuth, "newAuth");
        final boolean defaultHeader = newAuth == ProviderAuth.API_KEY && apiKeyHeader.isBlank();
        final ProviderDraft signedIn = withAuth(newAuth);
        return defaultHeader ? signedIn.withApiKeyHeader(DEFAULT_KEY_HEADER) : signedIn;
    }

    /**
     * Tells whether the sign-in's variable has a value.
     *
     * @param environment where variables are looked up
     * @return {@link CredentialStatus#NONE} without a sign-in or a variable name
     */
    public CredentialStatus credentialStatus(final EnvironmentVariables environment) {
        Objects.requireNonNull(environment, "environment");
        if (auth == ProviderAuth.NONE || credentialRef.isBlank()) {
            return CredentialStatus.NONE;
        }
        return environment.isSet(credentialRef.strip()) ? CredentialStatus.SET : CredentialStatus.NOT_SET;
    }

    /**
     * Turns the text into a provider description, refusing what the rules refuse.
     *
     * @param id the registry key
     * @return the description, or a {@code validation} error naming the first problem and never a typed value
     */
    public Result<ProviderConfig> toConfig(final String id) {
        Objects.requireNonNull(id, "id");
        final Optional<FormFinding> blocking =
                findings().stream().filter(FormFinding::blocking).findFirst();
        if (blocking.isPresent()) {
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Invalid provider",
                    "The provider settings are not valid yet: " + blocking.get().field()));
        }
        return Result.ok(build(
                id,
                ProviderFindings.parseSeconds(connectSeconds),
                ProviderFindings.parseSeconds(requestSeconds),
                URI.create(address.strip())));
    }

    /**
     * Judges the draft: what is missing or malformed here, then what the shared provider rules say.
     *
     * @return blocking findings and advice in the order the form shows them; empty when all is well
     */
    public List<FormFinding> findings() {
        final List<FormFinding> found = new ArrayList<>();
        if (name.isBlank()) {
            found.add(ProviderFindings.blocking(MessageKey.SETTINGS_ERROR_NAME, Field.NAME));
        }
        final boolean connectOk = ProviderFindings.isUsableSeconds(connectSeconds);
        final boolean requestOk = ProviderFindings.isUsableSeconds(requestSeconds);
        ProviderFindings.timeout(connectSeconds, Field.CONNECT_TIMEOUT).ifPresent(found::add);
        ProviderFindings.timeout(requestSeconds, Field.REQUEST_TIMEOUT).ifPresent(found::add);
        final @Nullable URI uri = parseAddress();
        if (uri == null) {
            found.add(ProviderFindings.blocking(MessageKey.SETTINGS_ERROR_ADDRESS, Field.ADDRESS));
        }
        final long connect = connectOk ? ProviderFindings.parseSeconds(connectSeconds) : 1;
        final long request = requestOk ? ProviderFindings.parseSeconds(requestSeconds) : 1;
        // The other rules are judged even when the address cannot be read, against a stand-in whose findings are
        // dropped.
        final URI judged = uri != null ? uri : URI.create(STAND_IN_ADDRESS);
        ProviderConfigRules.check(build("draft", connect, request, judged)).findings().stream()
                .map(ProviderFindings::mapped)
                .filter(finding -> uri != null || finding.field() != Field.ADDRESS)
                .forEach(found::add);
        found.addAll(headerFindings());
        return found;
    }

    private List<FormFinding> headerFindings() {
        final List<FormFinding> found = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (final HeaderRow row : headers) {
            final String header = row.name().strip().toLowerCase(Locale.ROOT);
            if (header.isEmpty() && !row.value().isBlank()) {
                found.add(ProviderFindings.blocking(MessageKey.SETTINGS_ERROR_HEADER_NAME_MISSING, Field.HEADERS));
            } else if (!header.isEmpty() && !seen.add(header)) {
                found.add(new FormFinding(
                        MessageKey.SETTINGS_ERROR_HEADER_DUPLICATE, List.of(header), Field.HEADERS, true));
            }
        }
        return found;
    }

    private ProviderConfig build(final String id, final long connect, final long request, final URI uri) {
        final Map<String, String> extra = new LinkedHashMap<>();
        for (final HeaderRow row : headers) {
            if (!row.name().isBlank()) {
                extra.put(row.name().strip(), row.value().strip());
            }
        }
        final boolean signedIn = auth != ProviderAuth.NONE;
        return new ProviderConfig(
                id,
                kind,
                uri,
                Duration.ofSeconds(connect),
                Duration.ofSeconds(request),
                name.strip(),
                auth,
                auth == ProviderAuth.API_KEY ? nullIfBlank(apiKeyHeader) : null,
                signedIn ? nullIfBlank(credentialRef) : null,
                nullIfBlank(modelsPath),
                nullIfBlank(chatPath),
                extra,
                customModels.stream()
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .distinct()
                        .toList());
    }

    private @Nullable URI parseAddress() {
        final String text = address.strip();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new URI(text);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String presetAddress(final ProviderKind kind) {
        return kind == ProviderKind.OLLAMA ? OLLAMA_ADDRESS : OPENAI_ADDRESS;
    }

    private static String orEmpty(final @Nullable String value) {
        return value == null ? "" : value;
    }

    private static @Nullable String nullIfBlank(final String value) {
        return value.isBlank() ? null : value.strip();
    }
}
