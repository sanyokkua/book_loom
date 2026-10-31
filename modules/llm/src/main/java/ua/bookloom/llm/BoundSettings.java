package ua.bookloom.llm;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.client.MessageShaper;
import ua.bookloom.llm.profile.BuiltInRows;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.profile.ProfileLog;
import ua.bookloom.llm.profile.ReasoningChoice;
import ua.bookloom.llm.profile.ResolvedProfile;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.provider.RejectedCapability;
import ua.bookloom.llm.provider.SettingDrops;
import ua.bookloom.llm.response.ReasoningScrubber;

/**
 * One bound model's stored settings and profile, read afresh for every call, so a choice the person saves between two
 * calls applies to the next one.
 */
@Slf4j
final class BoundSettings {

    private final String providerId;
    private final String modelId;
    private final BiFunction<String, String, ModelSettings> settings;
    private final @Nullable RefusalMemory refusals;
    private final Function<ModelSettings, ResolvedProfile> profiles;
    private final AtomicBoolean profileLogged = new AtomicBoolean();
    private final AtomicBoolean leftOutWarned = new AtomicBoolean();

    BoundSettings(
            final String providerId,
            final String modelId,
            final BiFunction<String, String, ModelSettings> settings,
            final @Nullable RefusalMemory refusals,
            final Function<ModelSettings, ResolvedProfile> profiles) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.refusals = refusals;
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    /**
     * Resolves a model built without the session's facts: the built-in row its name picks, with the person's
     * overrides.
     */
    static Function<ModelSettings, ResolvedProfile> unrowed(final ProviderClient client, final String modelId) {
        final ModelProfiles profiles = new ModelProfiles(new BuiltInRows());
        final ServerEngine engine = client.kind() == ProviderKind.OLLAMA ? ServerEngine.OLLAMA : ServerEngine.GENERIC;
        return stored -> profiles.resolve(engine, modelId, ModelFacts.unknown(), stored);
    }

    @Nullable
    Integer contextWindow() {
        return settings.apply(providerId, modelId).contextWindow();
    }

    // The first ask is the run's start, which the run log records once; the profile is resolved afresh every time.
    ModelProfile profile() {
        final ModelSettings stored = settings.apply(providerId, modelId);
        final ResolvedProfile resolved = profiles.apply(stored);
        if (profileLogged.compareAndSet(false, true)) {
            ProfileLog.runStart(modelId, resolved, stored.contextWindow());
        }
        return resolved.profile();
    }

    // Read once per call, so every retry of it keeps the same settings and a save lands on the next call. The
    // refusals the model's record already holds are stripped here and join the set of settings not to send again.
    Applied applied(final ChatRequest request, final EnumSet<RejectedCapability> downgraded) {
        final ModelSettings stored = settings.apply(providerId, modelId);
        final ModelProfile profile = profiles.apply(stored).profile();
        final Double temperature = temperatureOf(profile.sampling(), stored, request);
        final Integer cap = StoredLimits.capped(request.maxOutputTokens(), stored.maxOutput());
        final OutputLengthField field = StoredLimits.outputField(request, stored);
        if (refusals != null) {
            downgraded.addAll(refusals.remembered(stored, formatName(request)));
        }
        log.debug(
                "Applied model settings model={} kind={} temperatureChoice={} temperature={} cap={} outputField={}"
                        + " refused={} reasoning={} batch={} sampling={}",
                modelId,
                request.callKind(),
                stored.temperatureChoice(),
                temperature,
                cap,
                field,
                downgraded,
                profile.reasoning().kind(),
                profile.batchSize(),
                profile.sampling().mode());
        final ChatRequest settled = sampled(profile.sampling(), request).withModelSettings(temperature, cap, field);
        final ChatRequest shaped = MessageShaper.shaped(profile, onItsRung(profile, reasoned(profile, settled)));
        return new Applied(SettingDrops.stripAll(shaped, downgraded), profile);
    }

    // The person's Custom temperature wins; else the temperature choice on the same tab decides.
    private static @Nullable Double temperatureOf(
            final SamplingOverride sampling, final ModelSettings stored, final ChatRequest request) {
        if (sampling.mode() == SamplingOverride.Mode.CUSTOM && sampling.temperature() != null) {
            return sampling.temperature();
        }
        return switch (stored.temperatureChoice()) {
            case CHOOSE -> request.temperature();
            case VALUE -> stored.temperature();
            case OMIT -> null;
        };
    }

    // The profile's default sends no sampler beyond the temperature: an engine whose own defaults are not neutral gets
    // neutral values from its traits. Custom sends each value the person typed; a value left empty keeps the profile's.
    private static ChatRequest sampled(final SamplingOverride sampling, final ChatRequest request) {
        if (sampling.mode() == SamplingOverride.Mode.PROFILE_DEFAULT) {
            return request;
        }
        final SamplingParams base = request.samplingOrNone();
        final SamplingParams custom = new SamplingParams(
                sampling.topP() != null ? sampling.topP() : base.topP(),
                sampling.topK() != null ? sampling.topK() : base.topK(),
                sampling.minP() != null ? sampling.minP() : base.minP(),
                base.repeatPenalty(),
                base.withheld());
        log.debug(
                "Sampling kind={} source=custom temperature={} topK={} topP={} minP={}",
                request.callKind(),
                sampling.temperature(),
                custom.topK(),
                custom.topP(),
                custom.minP());
        return request.withSampling(custom);
    }

    // A structured answer starts on the schema rung unless the row says the model starts on the prompt-only rung, and a
    // call that thinks never carries a schema: enforced from the first token, the schema would hold the thinking too.
    private static ChatRequest onItsRung(final ModelProfile profile, final ChatRequest request) {
        if (request.responseFormat() == null) {
            return request;
        }
        final boolean promptOnlyStart = profile.quirks().contains(ModelProfile.Quirk.PROMPT_ONLY_START);
        final boolean thinking = request.isThinking();
        log.debug(
                "Structured rung kind={} format={} promptOnlyStart={} thinking={} schemaSent={}",
                request.callKind(),
                formatName(request),
                promptOnlyStart,
                thinking,
                !promptOnlyStart && !thinking);
        return promptOnlyStart || thinking ? request.withoutResponseFormat() : request;
    }

    private static @Nullable String formatName(final ChatRequest request) {
        final ResponseFormat format = request.responseFormat();
        return format == null ? null : format.name();
    }

    /**
     * A reply as the caller gets it: the reasoning markup only this model writes is scrubbed, and the word joiners a
     * thinking switch was sent with are taken out again.
     */
    static ChatResponse finished(final ModelProfile profile, final ChatResponse response) {
        final boolean escaped = profile.quirks().contains(ModelProfile.Quirk.ESCAPE_THINK_TOGGLES);
        if (profile.markupFilters().isEmpty() && !escaped) {
            return response;
        }
        String text = ReasoningScrubber.scrub(response.content(), profile.markupFilters());
        text = escaped ? MessageShaper.unescaped(text) : text;
        log.debug(
                "Reply finished for the profile filters={} escaped={} before={} after={}",
                profile.markupFilters(),
                escaped,
                response.content().length(),
                text.length());
        return new ChatResponse(text, response.finishReason(), response.usage());
    }

    /**
     * One call's request as it goes out and the profile it was shaped by.
     *
     * @param request the request to send; non-null
     * @param profile the model's profile at the time of the call; non-null
     */
    record Applied(ChatRequest request, ModelProfile profile) {}

    // The reasoning is decided after the stored cap, so the reserve is added to the cap the person chose. A control
    // left out is worth one WARN per model; every later call says so at DEBUG.
    private ChatRequest reasoned(final ModelProfile profile, final ChatRequest request) {
        final ReasoningChoice.Decision decision = ReasoningChoice.decide(profile, request);
        if (decision.leftOut() && leftOutWarned.compareAndSet(false, true)) {
            log.warn(
                    "No thinking control sent: the model or its server declares none that can be sent provider={}"
                            + " model={} control={} reserve={}",
                    providerId,
                    modelId,
                    profile.reasoning().kind(),
                    decision.reserves());
        }
        return ReasoningChoice.applied(decision, request);
    }
}
