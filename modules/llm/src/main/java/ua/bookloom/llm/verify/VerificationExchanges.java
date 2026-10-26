package ua.bookloom.llm.verify;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.http.RequestHeaders;
import ua.bookloom.llm.provider.EndpointPaths;

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
        return build(VerificationStage.CONNECTION, config, outcome, "", "", null, false);
    }

    static VerificationExchange models(
            final ProviderConfig config, final StageOutcome outcome, final List<String> listedIds) {
        return build(VerificationStage.MODELS, config, outcome, "", String.join("\n", listedIds), null, false);
    }

    static VerificationExchange inference(
            final ProviderConfig config, final InferenceProbe probe, final String requestBody) {
        final ChatResponse response = probe.response();
        return build(
                VerificationStage.INFERENCE,
                config,
                probe.outcome(),
                requestBody,
                response == null ? "" : response.content(),
                response == null ? null : response.usage(),
                probe.structured());
    }

    private static VerificationExchange build(
            final VerificationStage stage,
            final ProviderConfig config,
            final StageOutcome outcome,
            final String requestBody,
            final String responseBody,
            final @Nullable TokenUsage usage,
            final boolean structured) {
        final boolean post = stage == VerificationStage.INFERENCE;
        final String headers = RequestHeaders.masked(config).entrySet().stream()
                .map(header -> header.getKey() + ": " + header.getValue())
                .collect(Collectors.joining("\n"));
        return new VerificationExchange(
                post ? "POST" : "GET",
                url(stage, config),
                requestBody,
                responseBody,
                httpStatus(outcome),
                firstTokenMs(usage),
                tokensPerSecond(usage),
                structured,
                headers,
                usage == null ? null : usage.prompt(),
                usage == null ? null : usage.completion());
    }

    // A stage the server answered with an error status keeps that status, even when the stage still passed softly (the
    // error's safe details carry it); a failure that never reached a server has none.
    private static int httpStatus(final StageOutcome outcome) {
        final AppError error = outcome.error();
        final String details = error == null ? null : error.details();
        final Matcher found = details == null ? null : STATUS.matcher(details);
        if (found != null && found.find()) {
            return Integer.parseInt(found.group(1));
        }
        return outcome.status() == StageStatus.FAILED ? 0 : OK;
    }

    private static @Nullable Long firstTokenMs(final @Nullable TokenUsage usage) {
        return usage == null || usage.promptEval() == null
                ? null
                : Long.valueOf(usage.promptEval().toMillis());
    }

    private static @Nullable Double tokensPerSecond(final @Nullable TokenUsage usage) {
        if (usage == null || usage.completion() == null || usage.generation() == null) {
            return null;
        }
        final Duration generation = usage.generation();
        final double seconds = generation.toNanos() / NANOS_PER_SECOND;
        return seconds > 0 ? usage.completion() / seconds : null;
    }

    private static String url(final VerificationStage stage, final ProviderConfig config) {
        final boolean ollama = config.kind() == ProviderKind.OLLAMA;
        final String path =
                switch (stage) {
                    case CONNECTION -> ollama ? "/api/version" : EndpointPaths.modelsPath(config, "/models");
                    case MODELS -> EndpointPaths.modelsPath(config, ollama ? "/api/tags" : "/models");
                    case INFERENCE -> EndpointPaths.chatPath(config, ollama ? "/api/chat" : "/chat/completions");
                };
        // A user name or password in a stored address never reaches the dialog or the curl line copied from it.
        final String base = USER_INFO
                .matcher(config.baseUrl().toString())
                .replaceFirst("$1")
                .replaceFirst("/+$", "");
        return base + "/" + path.replaceFirst("^/+", "");
    }
}
