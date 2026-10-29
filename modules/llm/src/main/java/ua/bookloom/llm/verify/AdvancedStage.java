package ua.bookloom.llm.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProbeVerdicts.Outcome;
import ua.bookloom.api.llm.ProbeVerdicts.Verdict;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * Runs the Advanced features checks through the gate and keeps what they found on the model's settings. Verdicts are
 * written through the repository's {@code update}, so a refusal a run records meanwhile is kept.
 */
@Slf4j
final class AdvancedStage {

    private final RetriedCalls calls;
    private final ObjectMapper mapper;
    private final ModelSettingsRepository settings;
    private final Clock clock;

    AdvancedStage(
            final RetriedCalls calls,
            final ObjectMapper mapper,
            final ModelSettingsRepository settings,
            final Clock clock) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    StageOutcome run(final ProviderClient client, final ProviderConfig config, final String modelId) {
        log.debug("Provider verification advanced stage started provider={} model={}", config.id(), modelId);
        final AdvancedChecks.Sender sender = (request, offSwitch) ->
                calls.run(() -> client.checkChat(modelId, request, offSwitch), VerificationStage.ADVANCED, config.id());
        final AdvancedChecks.Findings found = new AdvancedChecks(config, mapper, sender, clock.instant()).run();
        keep(config.id(), modelId, found.verdicts(), found.capField());
        final String note = note(found);
        log.info(
                "Advanced features check finished provider={} model={} engine={} verdicts=[{}] stopped={}",
                config.id(),
                modelId,
                config.effectiveEngine(),
                note,
                found.failure() == null ? "none" : found.failure().code());
        final AppError failure = found.failure();
        return failure == null
                ? StageOutcomes.passed(VerificationStage.ADVANCED, note)
                : StageOutcomes.failed(VerificationStage.ADVANCED, failure);
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

    // Machine words for the stage's note and the log; the dialog's own words for each verdict come with its rows.
    private static String note(final AdvancedChecks.Findings found) {
        final ProbeVerdicts verdicts = found.verdicts();
        final List<String> parts = new ArrayList<>(4);
        add(parts, "structured answers", verdicts.answerSchema());
        final ProbeVerdicts.ThinkingVerdict thinking = verdicts.thinkingSwitch();
        if (thinking != null) {
            final String named = thinking.switchName();
            parts.add(
                    "reasoning off: " + (named == null ? word(thinking.verdict().outcome()) : named));
        }
        add(parts, "top_k/min_p", verdicts.extraSamplers());
        if (verdicts.capField() != null) {
            final OutputLengthField field = found.capField();
            parts.add("answer cap: " + (field == null ? "none" : field.wireName()));
        }
        return String.join("; ", parts);
    }

    private static void add(final List<String> parts, final String name, final @Nullable Verdict verdict) {
        if (verdict != null) {
            parts.add(name + ": " + word(verdict.outcome()));
        }
    }

    private static String word(final Outcome outcome) {
        return outcome.name().toLowerCase(Locale.ROOT);
    }
}
