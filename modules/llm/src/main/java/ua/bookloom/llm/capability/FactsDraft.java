package ua.bookloom.llm.capability;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelFacts;

/** What one facts read has gathered so far; each engine's reader fills the parts its server answers. */
final class FactsDraft {
    @Nullable
    String architecture;

    @Nullable
    String family;

    @Nullable
    String format;

    @Nullable
    String quant;

    @Nullable
    String parameterSize;

    List<String> capabilities = List.of();
    List<String> thinkingOptions = List.of();

    @Nullable
    String thinkingDefault;

    @Nullable
    String renderer;

    @Nullable
    String parser;

    @Nullable
    String runner;

    @Nullable
    String type;

    @Nullable
    String state;

    @Nullable
    String compatibility;

    @Nullable
    Integer contextLength;

    @Nullable
    Integer loadedContext;

    List<String> supportedParameters = List.of();

    @Nullable
    Integer maxCompletionTokens;

    boolean reasoningMandatory;

    ModelFacts toFacts() {
        return new ModelFacts(
                architecture,
                family,
                format,
                quant,
                parameterSize,
                capabilities,
                thinkingOptions,
                thinkingDefault,
                renderer,
                parser,
                runner,
                type,
                state,
                compatibility,
                contextLength,
                loadedContext,
                supportedParameters,
                maxCompletionTokens,
                reasoningMandatory);
    }
}
