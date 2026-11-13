package ua.bookloom.llm.verify;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationListener;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.engine.EngineTraits.OffSwitch;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * Tells a {@link VerificationListener} of one model request just before it goes out, built the way the finished
 * report builds it: the client's own wire body, the masked headers, and the body laid out for reading. Called inside
 * the gated attempt, so each retry is heard too. Nothing is built when nobody listens.
 */
@Slf4j
final class RequestAnnouncer {

    private final VerificationListener listener;
    private final ProviderConfig config;
    private final ProviderClient client;
    private final String modelId;
    private final PrettyJson pretty;

    /**
     * Binds the announcements to one model on one client.
     *
     * @param listener who hears
     * @param client the client the requests are sent through, whose body and address are announced
     */
    RequestAnnouncer(
            final VerificationListener listener,
            final ProviderConfig config,
            final ProviderClient client,
            final String modelId,
            final PrettyJson pretty) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.pretty = Objects.requireNonNull(pretty, "pretty");
    }

    /**
     * The same listener for another client, as the Advanced stage sends through a client of its own.
     *
     * @param other the client the next requests go through
     * @return an announcer over {@code other}
     */
    RequestAnnouncer on(final ProviderClient other) {
        return new RequestAnnouncer(listener, config, other, modelId, pretty);
    }

    /** Announces the inference stage's probe. */
    void inference(final ChatRequest request) {
        if (!VerificationListener.NONE.equals(listener)) {
            announce(VerificationStage.INFERENCE, null, client.requestBody(modelId, request));
        }
    }

    /** Announces one call of an Advanced check. */
    void check(final AdvancedCheck check, final ChatRequest request, final @Nullable OffSwitch offSwitch) {
        if (!VerificationListener.NONE.equals(listener)) {
            announce(VerificationStage.ADVANCED, check, client.checkRequestBody(modelId, request, offSwitch));
        }
    }

    // A listener that fails must not fail the check: the request is sent anyway and the report still arrives.
    private void announce(final VerificationStage stage, final @Nullable AdvancedCheck check, final String body) {
        final VerificationExchange request = VerificationExchanges.request(
                stage, config, body, pretty.of(body), client.chatUrl().orElse(null));
        log.debug(
                "Provider verification request announced provider={} stage={} check={} bodyChars={}",
                config.id(),
                stage,
                check,
                body.length());
        try {
            listener.requestSent(stage, check, request);
        } catch (RuntimeException failure) {
            log.warn(
                    "Provider verification listener failed on a request provider={} stage={}",
                    config.id(),
                    stage,
                    failure);
        }
    }
}
