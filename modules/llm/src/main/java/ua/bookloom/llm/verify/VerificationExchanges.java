package ua.bookloom.llm.verify;

import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.http.RequestHeaders;
import ua.bookloom.llm.provider.EndpointPaths;

/**
 * Describes what a verification stage sent, for the Settings test dialog. Headers come from the description alone with
 * every secret masked, so the key is never in the exchange.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class VerificationExchanges {

    static VerificationExchange forStage(
            VerificationStage stage, ProviderConfig config, StageStatus status, String requestBody) {
        final boolean post = stage == VerificationStage.INFERENCE;
        final String headers = RequestHeaders.masked(config).entrySet().stream()
                .map(header -> header.getKey() + ": " + header.getValue())
                .collect(Collectors.joining("\n"));
        return new VerificationExchange(
                post ? "POST" : "GET",
                url(stage, config),
                post ? requestBody : "",
                "",
                status == StageStatus.FAILED ? 0 : 200,
                null,
                null,
                false,
                headers);
    }

    private static String url(VerificationStage stage, ProviderConfig config) {
        final boolean ollama = config.kind() == ProviderKind.OLLAMA;
        final String path =
                switch (stage) {
                    case CONNECTION -> ollama ? "/api/version" : EndpointPaths.modelsPath(config, "/models");
                    case MODELS -> EndpointPaths.modelsPath(config, ollama ? "/api/tags" : "/models");
                    case INFERENCE -> EndpointPaths.chatPath(config, ollama ? "/api/chat" : "/chat/completions");
                };
        final String base = config.baseUrl().toString().replaceFirst("/+$", "");
        return base + "/" + path.replaceFirst("^/+", "");
    }
}
