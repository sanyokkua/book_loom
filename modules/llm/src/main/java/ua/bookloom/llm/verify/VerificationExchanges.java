package ua.bookloom.llm.verify;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ProbeCall;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.http.RequestHeaders;
import ua.bookloom.llm.provider.EndpointPaths;
import ua.bookloom.util.text.Slashes;

/**
 * Describes what a verification stage sent and got back, for the Settings test dialog. Headers come from the
 * description alone with every secret masked, so the key is never in the exchange.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class VerificationExchanges {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final int OK = 200;
    private static final Pattern USER_INFO = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://)[^/?#@]*@");
    private static final Pattern STATUS = Pattern.compile("httpStatus=(\\d{3})");

    static VerificationExchange connection(final ProviderConfig config, final StageOutcome outcome) {
        return build(VerificationStage.CONNECTION, config, outcome, Bodies.NONE, null, false, List.of());
    }

    static VerificationExchange models(
            final ProviderConfig config, final StageOutcome outcome, final List<String> listedIds) {
        final String ids = String.join("\n", listedIds);
        return build(VerificationStage.MODELS, config, outcome, new Bodies("", "", ids, ids), null, false, List.of());
    }

    /**
     * The inference stage's exchange.
     *
     * @param bodies the wire body and the reply, each also laid out for reading
     * @param chatUrl the address the client posted the chat to (LM Studio's own route or the OpenAI one), or null
     *     when the client cannot say and the address is built from the description
     */
    static VerificationExchange inference(
            final ProviderConfig config, final InferenceProbe probe, final Bodies bodies, final @Nullable URI chatUrl) {
        final ChatResponse response = probe.response();
        final VerificationExchange built = build(
                VerificationStage.INFERENCE,
                config,
                probe.outcome(),
                bodies,
                response == null ? null : response.usage(),
                probe.structured(),
                List.of());
        return chatUrl == null ? built : withUrl(built, chatUrl);
    }

    /**
     * The Advanced features stage's exchange: several calls, each kept in {@link VerificationExchange#calls()}; the
     * body is the last call's, so Copy as curl repeats it, and the reply is the stage's verdict note.
     *
     * @param calls the calls the checks made, in the order sent
     * @param chatUrl the address the client posts chats to, or null when the client cannot say
     */
    static VerificationExchange advanced(
            final ProviderConfig config,
            final StageOutcome outcome,
            final List<ProbeCall> calls,
            final @Nullable URI chatUrl) {
        final String note = Objects.requireNonNullElse(outcome.note(), "");
        final ProbeCall last = calls.isEmpty() ? null : calls.getLast();
        final Bodies bodies = last == null
                ? new Bodies("", "", note, note)
                : new Bodies(last.requestBody(), last.shownRequestBody(), note, note);
        final VerificationExchange built =
                build(VerificationStage.ADVANCED, config, outcome, bodies, null, false, calls);
        return chatUrl == null ? built : withUrl(built, chatUrl);
    }

    /**
     * A request about to be sent, with no reply yet, for {@link ua.bookloom.api.llm.VerificationListener}.
     *
     * @param stage the stage sending it
     * @param body the wire body
     * @param shownBody the same body laid out for reading
     * @param chatUrl the address the client posts chats to, or null when the client cannot say
     */
    static VerificationExchange request(
            final VerificationStage stage,
            final ProviderConfig config,
            final String body,
            final String shownBody,
            final @Nullable URI chatUrl) {
        final VerificationExchange built = new VerificationExchange(
                "POST",
                url(stage, config),
                body,
                "",
                0,
                null,
                null,
                false,
                maskedHeaders(config),
                null,
                null,
                shownBody,
                "",
                null,
                List.of());
        return chatUrl == null ? built : withUrl(built, chatUrl);
    }

    /**
     * The tokens per second a reply's usage gives.
     *
     * @return the speed, or null when the reply did not say how many tokens it generated in how long
     */
    static @Nullable Double tokensPerSecond(final @Nullable TokenUsage usage) {
        if (usage == null || usage.completion() == null || usage.generation() == null) {
            return null;
        }
        final Duration generation = usage.generation();
        final double seconds = generation.toNanos() / NANOS_PER_SECOND;
        return seconds > 0 ? usage.completion() / seconds : null;
    }

    /**
     * The status a failed call carried in its safe details.
     *
     * @return the HTTP status, or zero when the failure never reached a server or did not say
     */
    static int statusOf(final @Nullable AppError error) {
        final String details = error == null ? null : error.details();
        final Matcher found = details == null ? null : STATUS.matcher(details);
        return found != null && found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    private static VerificationExchange withUrl(final VerificationExchange exchange, final URI url) {
        final String shown = USER_INFO.matcher(url.toString()).replaceFirst("$1");
        return new VerificationExchange(
                exchange.method(),
                shown,
                exchange.requestBody(),
                exchange.responseBody(),
                exchange.httpStatus(),
                exchange.firstTokenMs(),
                exchange.tokensPerSecond(),
                exchange.structured(),
                exchange.requestHeaders(),
                exchange.promptTokens(),
                exchange.completionTokens(),
                exchange.shownRequestBody(),
                exchange.shownResponseBody(),
                exchange.loadMs(),
                exchange.calls());
    }

    private static VerificationExchange build(
            final VerificationStage stage,
            final ProviderConfig config,
            final StageOutcome outcome,
            final Bodies bodies,
            final @Nullable TokenUsage usage,
            final boolean structured,
            final List<ProbeCall> calls) {
        final boolean post = stage == VerificationStage.INFERENCE || stage == VerificationStage.ADVANCED;
        return new VerificationExchange(
                post ? "POST" : "GET",
                url(stage, config),
                bodies.request(),
                bodies.response(),
                httpStatus(outcome),
                firstTokenMs(usage),
                tokensPerSecond(usage),
                structured,
                maskedHeaders(config),
                usage == null ? null : usage.prompt(),
                usage == null ? null : usage.completion(),
                bodies.shownRequest(),
                bodies.shownResponse(),
                usage == null || usage.load() == null
                        ? null
                        : Long.valueOf(usage.load().toMillis()),
                calls);
    }

    private static String maskedHeaders(final ProviderConfig config) {
        return RequestHeaders.masked(config).entrySet().stream()
                .map(header -> header.getKey() + ": " + header.getValue())
                .collect(Collectors.joining("\n"));
    }

    /**
     * The bodies of one exchange: each as sent or received, and laid out for reading.
     *
     * @param request the wire body, or empty when there was none
     * @param shownRequest the request body laid out for reading
     * @param response the reply as received, or empty when none arrived
     * @param shownResponse the reply laid out for reading
     */
    record Bodies(String request, String shownRequest, String response, String shownResponse) {

        static final Bodies NONE = new Bodies("", "", "", "");
    }

    // A stage the server answered with an error status keeps that status, even when the stage still passed softly (the
    // error's safe details carry it); a failure that never reached a server has none.
    private static int httpStatus(final StageOutcome outcome) {
        final int reported = statusOf(outcome.error());
        if (reported != 0) {
            return reported;
        }
        return outcome.status() == StageStatus.FAILED ? 0 : OK;
    }

    private static @Nullable Long firstTokenMs(final @Nullable TokenUsage usage) {
        return usage == null || usage.promptEval() == null
                ? null
                : Long.valueOf(usage.promptEval().toMillis());
    }

    private static String url(final VerificationStage stage, final ProviderConfig config) {
        final boolean ollama = config.kind() == ProviderKind.OLLAMA;
        final String path =
                switch (stage) {
                    case CONNECTION -> ollama ? "/api/version" : EndpointPaths.modelsPath(config, "/models");
                    case MODELS -> EndpointPaths.modelsPath(config, ollama ? "/api/tags" : "/models");
                    case INFERENCE, ADVANCED ->
                        EndpointPaths.chatPath(config, ollama ? "/api/chat" : "/chat/completions");
                };
        // A user name or password in a stored address never reaches the dialog or the curl line copied from it.
        final String base = USER_INFO.matcher(config.baseUrl().toString()).replaceFirst("$1");
        return Slashes.trimTrailing(base) + "/" + Slashes.trimLeading(path);
    }
}
