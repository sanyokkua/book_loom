package ua.bookloom.llm;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelInputForm;
import ua.bookloom.api.llm.ModelNotes;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.PastedModel;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.engine.CheckedModelId;
import ua.bookloom.llm.knowledge.LanguageAdvisor;
import ua.bookloom.llm.knowledge.QuantAdvisor;
import ua.bookloom.llm.profile.ModelIdentityParser;
import ua.bookloom.llm.profile.ModelInputNormalizer;
import ua.bookloom.llm.profile.ModelLabels;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.profile.ParsedName;
import ua.bookloom.llm.profile.PullCommand;
import ua.bookloom.llm.profile.ResolvedProfile;
import ua.bookloom.llm.profile.ThinkingHint;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;

/**
 * Detects a model's context length through the provider's own client. Like listing, it is a catalogue read and skips
 * the inference gate; a provider that cannot answer yields an unknown length, never an error.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ModelCapabilityService implements ModelCapabilities {

    private final ProviderConfigs configs;
    private final ProviderClientFactory clients;
    private final ModelProfiles profiles;
    private final ModelSettingsCache settings;
    private final QuantAdvisor quants;
    private final LanguageAdvisor languages;
    private static final String SELECTION = "selection";

    private final ModelIdentityParser parser = new ModelIdentityParser();
    private final ModelInputNormalizer normalizer = new ModelInputNormalizer();

    // Served by provider and model id, so the Models tab reads it before a chat model exists. A read that throws leaves
    // the server's facts unknown; the name alone still gives the identity and the default profile. This is the one
    // method here that asks the server, on a person's action only; what it read is held for the session.
    @Override
    public Result<ModelReport> report(ModelSelection selection) {
        Objects.requireNonNull(selection, SELECTION);
        log.debug("Reporting model providerId={} model={}", selection.providerId(), selection.modelId());
        return reportWith(selection, true);
    }

    @Override
    public Result<ModelReport> heldReport(final ModelSelection selection) {
        Objects.requireNonNull(selection, SELECTION);
        log.debug(
                "Reporting model from held facts providerId={} model={}", selection.providerId(), selection.modelId());
        return reportWith(selection, false);
    }

    private Result<ModelReport> reportWith(final ModelSelection selection, final boolean askServer) {
        final String providerId = selection.providerId();
        final String modelId = selection.modelId();
        try {
            final Optional<ProviderConfig> config = configs.find(providerId);
            if (config.isEmpty()) {
                log.debug("Model report refused branch=unknown-provider providerId={}", providerId);
                return Result.err(unknownProvider());
            }
            final @Nullable ModelFacts facts = askServer ? factsOf(config.get(), modelId) : held(providerId, modelId);
            return Result.ok(describe(config.get(), modelId, facts));
        } catch (RuntimeException cause) {
            log.warn("Model report failed providerId={} model={}", providerId, modelId, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal, "Model report failed", "BookLoom could not describe this model.", null, cause));
        }
    }

    private ModelReport describe(final ProviderConfig config, final String modelId, final @Nullable ModelFacts facts) {
        final ModelFacts known = facts == null ? ModelFacts.unknown() : facts;
        final ResolvedProfile resolved = resolve(config, modelId, known);
        final QuantAdvice quant = quants.advise(resolved.identity(), facts);
        final ModelReport report = resolved.report()
                .withAdvice(languages.declaration(resolved.identity()).orElse(null), quant)
                .withNotes(notes(config, modelId, resolved, known, quant));
        log.debug(
                "Model reported providerId={} model={} heldOrReadFacts={} contextLength={} loaded={} match={} verdict={}",
                config.id(),
                modelId,
                facts != null,
                report.contextLength(),
                report.loadedContext(),
                report.match(),
                report.verdict());
        return report;
    }

    private ResolvedProfile resolve(final ProviderConfig config, final String modelId, final ModelFacts facts) {
        return profiles.resolve(config.effectiveEngine(), modelId, facts, settings.current(config.id(), modelId));
    }

    private @Nullable ModelFacts held(final String providerId, final String modelId) {
        final Optional<ModelFacts> held = clients.heldFacts(providerId, modelId);
        log.debug(
                "Held facts providerId={} model={} branch={}", providerId, modelId, held.isPresent() ? "held" : "name");
        return held.orElse(null);
    }

    private ModelNotes notes(
            final ProviderConfig config,
            final String modelId,
            final ResolvedProfile resolved,
            final ModelFacts facts,
            final QuantAdvice quant) {
        final ModelIdentity identity = resolved.identity();
        final String label = ModelLabels.withSize(identity);
        final String libraryTag = ThinkingHint.libraryTag(
                        config.effectiveEngine(),
                        modelId,
                        identity,
                        resolved.profile().reasoning())
                .orElse(null);
        return new ModelNotes(
                label.isEmpty() ? modelId : label,
                ModelLabels.of(identity),
                libraryTag,
                facts.quant(),
                languages.quantMeasured(identity, quant.token()));
    }

    // Offline: the facts are the ones a person's action already read this session, else the name alone.
    @Override
    public LanguageAdvice languageAdvice(
            final ModelSelection selection, final @Nullable String sourceTag, final @Nullable String targetTag) {
        Objects.requireNonNull(selection, SELECTION);
        log.debug(
                "Advising languages providerId={} model={} source={} target={}",
                selection.providerId(),
                selection.modelId(),
                sourceTag,
                targetTag);
        try {
            final Optional<ProviderConfig> config = configs.find(selection.providerId());
            if (config.isEmpty()) {
                log.debug("Language advice skipped branch=unknown-provider providerId={}", selection.providerId());
                return LanguageAdvice.NEUTRAL;
            }
            return adviseHeld(config.get(), selection.modelId(), sourceTag, targetTag);
        } catch (RuntimeException cause) {
            log.warn("Language advice degraded to neutral model={}", selection.modelId(), cause);
            return LanguageAdvice.NEUTRAL;
        }
    }

    private LanguageAdvice adviseHeld(
            final ProviderConfig config,
            final String modelId,
            final @Nullable String sourceTag,
            final @Nullable String targetTag) {
        final @Nullable ModelFacts facts = held(config.id(), modelId);
        final ResolvedProfile resolved = resolve(config, modelId, facts == null ? ModelFacts.unknown() : facts);
        return languages.advise(
                resolved.identity(),
                facts,
                sourceTag,
                targetTag,
                ModelLabels.of(resolved.identity()),
                resolved.baseModel());
    }

    @Override
    public String factsDate() {
        try {
            return languages.dataVersion();
        } catch (RuntimeException cause) {
            log.warn("Model knowledge date degraded to none", cause);
            return "";
        }
    }

    private ModelFacts factsOf(final ProviderConfig config, final String modelId) {
        try {
            return clients.create(config).facts(modelId);
        } catch (RuntimeException cause) {
            log.warn("Model facts degraded to unknown providerId={} model={}", config.id(), modelId, cause);
            return ModelFacts.unknown();
        }
    }

    // Only LM Studio's own list says whether a model is in memory; asking any other server would be a GET for nothing.
    @Override
    public boolean loadsWithOwnSettings(ModelSelection selection) {
        Objects.requireNonNull(selection, SELECTION);
        final Optional<ProviderConfig> config = configs.find(selection.providerId());
        if (config.isEmpty() || config.get().effectiveEngine() != ServerEngine.LM_STUDIO) {
            log.debug(
                    "Model load question skipped providerId={} branch={}",
                    selection.providerId(),
                    config.isEmpty() ? "unknown-provider" : "not-lm-studio");
            return false;
        }
        try {
            final Result<CheckedModelId> checked = clients.create(config.get()).recheckModelId(selection.modelId());
            final CheckedModelId found = checked.data();
            final boolean loads = found != null && Boolean.FALSE.equals(found.loaded());
            log.debug(
                    "Model load question providerId={} model={} loaded={} loadsWithOwnSettings={}",
                    selection.providerId(),
                    selection.modelId(),
                    found == null ? null : found.loaded(),
                    loads);
            return loads;
        } catch (Exception cause) {
            log.warn(
                    "Model load question degraded to no providerId={} model={}",
                    selection.providerId(),
                    selection.modelId(),
                    cause);
            return false;
        }
    }

    @Override
    public @Nullable QuantAdvice quantAdviceByName(final String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        log.debug("Quant advice by name model={}", modelId);
        try {
            return quants.advise(parser.parse(modelId).identity(), null);
        } catch (RuntimeException cause) {
            log.warn("Quant advice by name degraded to none model={}", modelId, cause);
            return null;
        }
    }

    // Pure text reading: the normalizer and parser never open a link or run a command (PastedTextIsNeverActedOnTest).
    @Override
    public Optional<PastedModel> readPasted(final String text, final ProviderKind kind) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        log.debug("Reading pasted text kind={} length={}", kind, text.length());
        try {
            return pasted(text, kind);
        } catch (RuntimeException cause) {
            log.warn("Pasted text could not be read kind={}", kind, cause);
            return Optional.empty();
        }
    }

    private Optional<PastedModel> pasted(final String text, final ProviderKind kind) {
        if (text.isBlank()) {
            log.debug("Pasted text is blank, nothing to explain");
            return Optional.empty();
        }
        final var input = normalizer.normalise(text);
        if (input.form() == ModelInputForm.MODEL_ID && input.repo() == null) {
            log.debug("Pasted text is a plain id, nothing to explain");
            return Optional.empty();
        }
        final ParsedName parsed = parser.parse(text);
        final String pull = kind == ProviderKind.OLLAMA ? PullCommand.of(parsed).orElse(null) : null;
        log.debug("Pasted text read form={} hasPull={}", input.form(), pull != null);
        return Optional.of(new PastedModel(
                input, ModelLabels.of(parsed.identity()), parsed.identity().quantLabel(), pull));
    }

    private static AppError unknownProvider() {
        return AppError.of(ErrorCode.validation, "Unknown provider", "The selected provider is not registered.");
    }

    @Override
    public Result<ContextLength> contextLength(String providerId, String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        log.debug("Detecting context length providerId={} model={}", providerId, modelId);
        final Optional<ProviderConfig> config = configs.find(providerId);
        if (config.isEmpty()) {
            log.debug("Context length detection refused branch=unknown-provider providerId={}", providerId);
            return Result.err(unknownProvider());
        }
        try {
            final ContextLength length = new ContextLength(
                    clients.create(config.get()).facts(modelId).contextLength());
            log.debug("Detected context length providerId={} model={} tokens={}", providerId, modelId, length.tokens());
            return Result.ok(length);
        } catch (Exception cause) {
            log.debug(
                    "Context length detection degraded to unknown providerId={} model={}", providerId, modelId, cause);
            return Result.ok(ContextLength.unknown());
        }
    }
}
