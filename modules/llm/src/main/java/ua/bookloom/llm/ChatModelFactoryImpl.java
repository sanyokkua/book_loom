package ua.bookloom.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.profile.ModelHosting;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.profile.ObservedReasoning;
import ua.bookloom.llm.profile.ProfileAdjustments;
import ua.bookloom.llm.profile.ResolvedProfile;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * Resolves pseudo or a registered real provider into a model bound to one model id.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ChatModelFactoryImpl implements ChatModelFactory {

    private static final String PSEUDO_PROVIDER_ID = "pseudo";

    private final ProviderConfigs configs;
    private final ProviderClientFactory clientFactory;
    private final InferenceGate gate;
    private final RetryPolicy retryPolicy;
    private final ObjectMapper mapper;
    private final ModelSettingsCache modelSettings;
    private final RefusalMemory refusals;
    private final CallLedger ledger;
    private final BuiltWrongLog builtWrong;
    private final CapIgnoredLog capIgnored;
    private final ModelProfiles profiles;
    private final EngineFacts engineFacts;
    private final ObservedReasoning observed;

    @Override
    public Result<ChatModel> create(ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        Result<ChatModel> result;
        try {
            result = createModel(selection);
        } catch (Exception cause) {
            result = Result.err(internalError(cause));
        }
        log.debug(
                "Created chat model providerId={} modelId={} result={}",
                selection.providerId(),
                selection.modelId(),
                resultDescription(result));
        return result;
    }

    private Result<ChatModel> createModel(ModelSelection selection) {
        if (selection.modelId().isBlank()) {
            log.debug("Chat model resolution stopped branch=blank-model providerId={}", selection.providerId());
            return rejected(selection, "The model id must not be blank.");
        }
        if (PSEUDO_PROVIDER_ID.equals(selection.providerId())) {
            log.debug("Chat model resolution branch=pseudo modelId={}", selection.modelId());
            return Result.ok(new PseudoChatModel(mapper));
        }
        final Optional<ProviderConfig> config = configs.find(selection.providerId());
        if (config.isEmpty()) {
            log.debug("Chat model resolution stopped branch=unknown-provider providerId={}", selection.providerId());
            return rejected(selection, "The requested provider is not available.");
        }
        final ProviderConfig registered = config.orElseThrow();
        log.debug(
                "Chat model resolution branch=registered providerId={} kind={} modelId={}",
                registered.id(),
                registered.kind(),
                selection.modelId());
        return Result.ok(gated(registered, selection.modelId()));
    }

    private ChatModel gated(ProviderConfig registered, String modelId) {
        return new GatedChatModel(
                clientFactory.create(registered),
                registered.id(),
                modelId,
                gate,
                retryPolicy,
                new ModelSettingsSource(
                        modelSettings::current,
                        refusals,
                        stored -> circumstances(
                                registered,
                                modelId,
                                profiles.resolve(
                                        registered.effectiveEngine(),
                                        modelId,
                                        requestFacts(
                                                registered,
                                                engineFacts
                                                        .lastRead(registered.id(), modelId)
                                                        .orElseGet(() -> engineFacts.withTemplateSwitch(
                                                                registered, modelId, ModelFacts.unknown()))),
                                        stored))),
                new CallObservers(ledger, builtWrong, capIgnored, observed));
    }

    // Where the model runs and whether it was seen thinking are known at the call, not from its name or its facts.
    private ResolvedProfile circumstances(ProviderConfig registered, String modelId, ResolvedProfile resolved) {
        final boolean hosted = ModelHosting.isHosted(registered.baseUrl(), modelId);
        final boolean thinking = observed.has(registered.id(), modelId);
        final ModelProfile adjusted = ProfileAdjustments.of(resolved.profile(), hosted, thinking);
        return adjusted.equals(resolved.profile())
                ? resolved
                : new ResolvedProfile(
                        adjusted,
                        resolved.identity(),
                        resolved.match(),
                        resolved.verdict(),
                        resolved.warnings(),
                        resolved.quant(),
                        resolved.baseModel());
    }

    // The Ollama-native client sends the window on every request, so the window Ollama loaded the model with last does
    // not bind the next call; every other connection gets the window the server fixed at load or start.
    private static ModelFacts requestFacts(ProviderConfig registered, ModelFacts facts) {
        if (registered.kind() != ProviderKind.OLLAMA || facts.loadedContext() == null) {
            return facts;
        }
        log.debug(
                "Loaded window set aside: the native client sends the window provider={} loaded={}",
                registered.id(),
                facts.loadedContext());
        return facts.withoutLoadedContext();
    }

    private Result<ChatModel> rejected(ModelSelection selection, String message) {
        final AppError error = AppError.of(ErrorCode.validation, "Invalid chat model selection", message);
        log.warn(
                "Refused chat model request providerId={} modelId={} code={}",
                selection.providerId(),
                selection.modelId(),
                error.code());
        return Result.err(error);
    }

    private static String resultDescription(Result<ChatModel> result) {
        return result.isOk()
                ? "ok"
                : Objects.requireNonNull(result.error(), "error").code().name();
    }

    private static AppError internalError(Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Chat model factory failure", "The chat model could not be created.", null, cause);
        log.error("Unexpected chat model factory failure code={}", error.code(), cause);
        return error;
    }
}
