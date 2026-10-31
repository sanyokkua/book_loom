package ua.bookloom.llm.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeCall;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProbeVerdicts.Outcome;
import ua.bookloom.api.llm.ProbeVerdicts.Verdict;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.provider.CheckReply;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * Runs the Advanced features checks through the gate and keeps what they found on the model's settings. Verdicts are
 * written through the repository's {@code update}, so a refusal a run records meanwhile is kept.
 */
@Slf4j
final class AdvancedStage {

    private static final int OK = 200;

    private final RetriedCalls calls;
    private final ObjectMapper mapper;
    private final ModelSettingsRepository settings;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final PrettyJson pretty;

    AdvancedStage(
            final RetriedCalls calls,
            final ObjectMapper mapper,
            final ModelSettingsRepository settings,
            final Clock clock,
            final LongSupplier nanoTime,
            final PrettyJson pretty) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.pretty = Objects.requireNonNull(pretty, "pretty");
    }

    /**
     * What the stage found and the calls it made, each timed, in the order sent.
     *
     * @param outcome the stage's verdict note, or the failure that stopped it
     * @param calls every call the checks made
     */
    record Ran(StageOutcome outcome, List<ProbeCall> calls) {}

    /**
     * Runs the checks.
     *
     * @param announcer told of each call just before it is sent, retries included
     */
    Ran run(
            final ProviderClient client,
            final ProviderConfig config,
            final String modelId,
            final RequestAnnouncer announcer) {
        log.debug("Provider verification advanced stage started provider={} model={}", config.id(), modelId);
        final List<ProbeCall> made = new ArrayList<>();
        final CheckSender sender = timedSender(client, config, modelId, announcer, made);
        final AdvancedChecks.Findings found =
                new AdvancedChecks(config, modelId, mapper, sender, clock.instant()).run();
        keep(config.id(), modelId, found.verdicts(), found.capField());
        final String note = note(found);
        log.info(
                "Advanced features check finished provider={} model={} engine={} verdicts=[{}] stopped={} calls={}",
                config.id(),
                modelId,
                config.effectiveEngine(),
                note,
                found.failure() == null ? "none" : found.failure().code(),
                made.size());
        final AppError failure = found.failure();
        return new Ran(
                failure == null
                        ? StageOutcomes.passed(VerificationStage.ADVANCED, note)
                        : StageOutcomes.failed(VerificationStage.ADVANCED, failure),
                List.copyOf(made));
    }

    // Each call is timed from the first attempt to its answer and kept with its check, for the test card's list.
    private CheckSender timedSender(
            final ProviderClient client,
            final ProviderConfig config,
            final String modelId,
            final RequestAnnouncer announcer,
            final List<ProbeCall> made) {
        return (check, request, offSwitch) -> {
            final long start = nanoTime.getAsLong();
            final ProviderCallResult<CheckReply> call = calls.run(
                    () -> {
                        announcer.check(check, request, offSwitch);
                        return client.checkChat(modelId, request, offSwitch);
                    },
                    VerificationStage.ADVANCED,
                    config.id());
            made.add(probeCall(check, client.checkRequestBody(modelId, request, offSwitch), call, start));
            return call;
        };
    }

    private ProbeCall probeCall(
            final AdvancedCheck check,
            final String body,
            final ProviderCallResult<CheckReply> call,
            final long startNanos) {
        final Duration elapsed = Duration.ofNanos(nanoTime.getAsLong() - startNanos);
        final CheckReply reply = call.result().data();
        final ChatResponse response = reply == null ? null : reply.response();
        final ProbeCall made = new ProbeCall(
                check,
                body,
                pretty.of(body),
                response == null ? "" : pretty.of(response.content()),
                response == null ? VerificationExchanges.statusOf(call.result().error()) : OK,
                elapsed,
                response == null ? null : VerificationExchanges.tokensPerSecond(response.usage()));
        log.debug(
                "Advanced check call timed check={} status={} elapsedMs={} tokensPerSecond={}",
                check,
                made.httpStatus(),
                elapsed.toMillis(),
                made.tokensPerSecond());
        return made;
    }

    /**
     * Keeps the inference stage's finding about the answer schema when its structured probe was not confirmed.
     *
     * @param outcome refused when the server rejected the schema, ignored when it answered something else
     */
    void keepSchema(final String providerId, final String modelId, final Outcome outcome) {
        log.debug("Inference stage schema verdict provider={} model={} outcome={}", providerId, modelId, outcome);
        final Verdict verdict = new Verdict(outcome, AdvancedChecks.SOURCE, clock.instant());
        keep(providerId, modelId, new ProbeVerdicts(verdict, null, null, null), null);
    }

    private void keep(
            final String providerId,
            final String modelId,
            final ProbeVerdicts found,
            final @Nullable OutputLengthField capField) {
        if (modelId.isBlank()) {
            log.debug("Check verdicts not kept provider={}: no model chosen", providerId);
            return;
        }
        final Result<ModelSettings> saved =
                settings.update(providerId, modelId, current -> changed(current, found, capField));
        final AppError failure = saved.error();
        if (failure != null) {
            log.warn("Check verdicts not kept provider={} model={} code={}", providerId, modelId, failure.code());
            return;
        }
        log.debug("Check verdicts kept provider={} model={} capField={}", providerId, modelId, capField);
    }

    private static ModelSettings changed(
            final ModelSettings current, final ProbeVerdicts found, final @Nullable OutputLengthField capField) {
        final ModelSettings merged =
                current.withProbeVerdicts(current.probeVerdicts().mergedWith(found));
        return capField == null
                ? merged
                : merged.withChoices(
                        merged.temperatureChoice(),
                        merged.temperature(),
                        merged.contextWindow(),
                        merged.maxOutput(),
                        capField);
    }

    // Machine words for the stage's note and the log (AdvancedCheck); the window words each pair in its own language.
    private static String note(final AdvancedChecks.Findings found) {
        final ProbeVerdicts verdicts = found.verdicts();
        final List<String> parts = new ArrayList<>(4);
        add(parts, AdvancedCheck.SCHEMA, verdicts.answerSchema());
        final ProbeVerdicts.ThinkingVerdict thinking = verdicts.thinkingSwitch();
        if (thinking != null) {
            final String named = thinking.switchName();
            parts.add(AdvancedCheck.THINKING.pair(
                    named == null ? word(thinking.verdict().outcome()) : named));
        }
        add(parts, AdvancedCheck.SAMPLERS, verdicts.extraSamplers());
        if (verdicts.capField() != null) {
            final OutputLengthField field = found.capField();
            parts.add(AdvancedCheck.CAP.pair(field == null ? AdvancedCheck.NO_FIELD : field.wireName()));
        }
        return String.join(AdvancedCheck.SEPARATOR, parts);
    }

    private static void add(final List<String> parts, final AdvancedCheck check, final @Nullable Verdict verdict) {
        if (verdict != null) {
            parts.add(check.pair(word(verdict.outcome())));
        }
    }

    private static String word(final Outcome outcome) {
        return outcome.name().toLowerCase(Locale.ROOT);
    }
}
