package ua.bookloom.pipeline;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.pipeline.RunRequest;

/**
 * Where a run's log lines get their facts: the ledger its summary counts calls from and the provider registry its
 * start line reads the server's kind from.
 *
 * @param ledger the ledger each run's summary counts its calls from
 * @param providers the registry a run's provider is placed in
 */
@Slf4j
public record RunLogSources(CallLedger ledger, ProviderConfigs providers) {

    private static final String UNKNOWN_PROVIDER_KIND = "unknown";

    /** Rejects a missing source. */
    @Inject
    public RunLogSources {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(providers, "providers");
    }

    /**
     * The facts one run's lines carry. Only the kind is logged, never the provider's id or address; a run the registry
     * cannot place (the pseudo model, a request with no selection) is {@code unknown}.
     */
    RunLogFacts factsFor(final RunRequest request) {
        final ModelSelection selection = request.model();
        final String kind = selection == null
                ? UNKNOWN_PROVIDER_KIND
                : providers
                        .find(selection.providerId())
                        .map(config -> config.kind().name())
                        .orElse(UNKNOWN_PROVIDER_KIND);
        log.debug("Run provider kind={} selected={}", kind, selection != null);
        return new RunLogFacts(ledger, kind);
    }
}
