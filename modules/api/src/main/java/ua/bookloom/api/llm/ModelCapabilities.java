package ua.bookloom.api.llm;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;

/**
 * Port for asking a registered provider what a model can take, so a run sizes its prompts to the window the model
 * really has instead of a guess.
 *
 * <p>Not inference: it never waits behind a running translation, and it is optional — it answers the model's
 * context length when the provider says it and {@link ContextLength#unknown()} otherwise.
 */
public interface ModelCapabilities {

    /**
     * Detects the context length of a model.
     *
     * @param providerId the id of a registered provider; never {@code null}
     * @param modelId the provider's model id; never {@code null}
     * @return the context length, {@link ContextLength#unknown()} when the provider cannot or does not say (a
     *     network or protocol failure is degraded, not reported), or {@link ua.bookloom.api.ErrorCode#validation} for an
     *     unregistered provider id (no request is sent)
     */
    Result<ContextLength> contextLength(String providerId, String modelId);

    /**
     * What BookLoom knows about a model, for display; served by provider and model id, so it can be read before a chat
     * model exists. Never fails the caller beyond an unknown provider.
     *
     * @param selection the provider and model to report; never {@code null}
     * @return the report, or {@link ua.bookloom.api.ErrorCode#validation} for an unregistered provider id
     */
    default Result<ModelReport> report(final ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        final Result<ContextLength> length = contextLength(selection.providerId(), selection.modelId());
        final ContextLength data = length.data();
        if (length.isErr() || data == null) {
            return Result.err(Objects.requireNonNull(length.error(), "error"));
        }
        return Result.ok(ModelReport.windowsOnly(data.tokens(), null));
    }

    /**
     * What BookLoom knows about a model without asking its server: the name, the shipped tables and the facts this
     * session already read for it (by {@link #report}), if any. Never sends a request, so a screen may ask it when it
     * is built; the server is read only on the person's actions (selecting a model, the Models tab, Test, Detect
     * again, Start, Continue).
     *
     * @param selection the provider and model to report; never {@code null}
     * @return the report, or {@link ua.bookloom.api.ErrorCode#validation} for an unregistered provider id
     */
    default Result<ModelReport> heldReport(final ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        return Result.ok(ModelReport.windowsOnly(null, null));
    }

    /**
     * Whether the server will load the model on the first call with settings of its own, so the window a run budgets
     * for may not be the one in use (LM Studio loads a model that is not in memory with its own defaults). Asked once
     * when a run is about to start; it never stops the run.
     *
     * @param selection the provider and model a run will use; never {@code null}
     * @return {@code true} if the server says the model is not loaded and loads it by itself, {@code false} when it is
     *     loaded, the server does not say, or the question could not be answered
     */
    default boolean loadsWithOwnSettings(final ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        return false;
    }

    /**
     * The context length a run should size against, with any failure degraded to "unknown": a run never stops because
     * its model's window could not be read.
     *
     * @param selection the provider and model a run will use; never {@code null}
     * @return the reported tokens, or empty when the provider did not say or the detection failed
     */
    default Optional<Integer> detectedTokens(final ModelSelection selection) {
        final Result<ContextLength> length = contextLength(selection.providerId(), selection.modelId());
        final ContextLength data = length.data();
        return length.isOk() && data != null ? data.detected() : Optional.empty();
    }

    /**
     * What the name alone says about a model's quant, for a chip beside a listed model. Pure and offline: no provider
     * is asked, so the tier stays {@code null} until the model's size is known.
     *
     * @param modelId the model id as listed or typed; never {@code null}
     * @return the advice, or {@code null} when this port cannot read names
     */
    default @Nullable QuantAdvice quantAdviceByName(final String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        return null;
    }

    /**
     * Reads a pasted link, command or file name into what the person pasted, without opening, running or sending it.
     *
     * @param text what was pasted; never {@code null}
     * @param kind the dialect of the provider the person is adding the model to; the pull command is offered for
     *     {@link ProviderKind#OLLAMA} only
     * @return the reading, or empty when the text is a plain model id, blank, or this port cannot read pastes
     */
    default Optional<PastedModel> readPasted(final String text, final ProviderKind kind) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        return Optional.empty();
    }

    /**
     * What the maker's language list says about a language pair for a model. Offline: it reads the name, the shipped
     * tables and the facts this session already holds for the model, and never asks the server.
     *
     * @param selection the provider and model; never {@code null}
     * @param sourceTag the source language tag, or null for none (Auto); that side is not judged
     * @param targetTag the target language tag, or null for none; that side is not judged
     * @return the advice, {@link LanguageAdvice#NEUTRAL} when this port reads no language tables
     */
    default LanguageAdvice languageAdvice(
            final ModelSelection selection, final @Nullable String sourceTag, final @Nullable String targetTag) {
        Objects.requireNonNull(selection, "selection");
        return LanguageAdvice.NEUTRAL;
    }

    /**
     * The date of the model knowledge shipped with the application.
     *
     * @return the date as {@code yyyy-MM-dd}, or an empty text when this port ships none
     */
    default String factsDate() {
        return "";
    }
}
