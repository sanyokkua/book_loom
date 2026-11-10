package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The words of the rows Settings › Models shows from the model report: BookLoom's verdict and the window with who sets
 * it. Pure: facts in, text out, so every engine's wording is proven without a window.
 */
@Slf4j
public final class ModelReportRows {

    private static final String NO_WARNINGS = "none";
    private static final String WARNING_SEPARATOR = " · ";

    private final Messages messages;

    public ModelReportRows(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * The verdict line.
     *
     * @param facts what the program says about the model
     * @return the line, or an empty string while the model is not reported; the licence note is not part of it, it has
     *     its own line
     */
    public String verdict(final ModelFacts facts) {
        Objects.requireNonNull(facts, "facts");
        final @Nullable String verdict = facts.verdict();
        if (verdict == null) {
            return "";
        }
        final String warnings = facts.warnings().stream()
                .filter(warning -> !ModelReport.WARNING_LICENCE_NOTE.equals(warning))
                .collect(Collectors.joining(WARNING_SEPARATOR));
        log.debug("verdict row: verdict '{}', {} warning(s) shown", verdict, warnings.isEmpty() ? 0 : 1);
        return messages.get(
                MessageKey.SETTINGS_MODELS_VERDICT,
                verdictToken(verdict),
                verdict,
                warnings.isEmpty() ? NO_WARNINGS : warnings);
    }

    /**
     * What the program found out about the window, and who sets it.
     *
     * @param entry the chosen model and its engine
     * @param facts what the program says about it
     * @return the words; a loaded figure only where the engine reports one
     */
    public String context(final ModelEntry entry, final ModelFacts facts) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(facts, "facts");
        final @Nullable Integer loaded = facts.loaded();
        if (entry.kind() == ProviderKind.OLLAMA) {
            return nativeOllama(facts);
        }
        final String line =
                switch (entry.engine()) {
                    case OLLAMA -> messages.get(MessageKey.SETTINGS_MODELS_FOUND_CONTEXT_OLLAMA_V1);
                    case LM_STUDIO ->
                        loaded == null
                                ? readable(facts)
                                : messages.get(MessageKey.SETTINGS_MODELS_FOUND_CONTEXT_LM_STUDIO, loaded);
                    case LLAMA_CPP ->
                        loaded == null
                                ? readable(facts)
                                : messages.get(MessageKey.SETTINGS_MODELS_FOUND_CONTEXT_LLAMA_SERVER, loaded);
                    case GENERIC, OPENROUTER, UNSLOTH -> readable(facts);
                };
        log.debug("context row: engine {}, window {}, loaded {}", entry.engine(), facts.contextLength(), loaded);
        return line;
    }

    private String nativeOllama(final ModelFacts facts) {
        final String read = readable(facts);
        final @Nullable Integer loaded = facts.loaded();
        return loaded == null || facts.contextLength() == null
                ? read
                : messages.get(MessageKey.SETTINGS_MODELS_FOUND_LOADED, read, loaded);
    }

    private String readable(final ModelFacts facts) {
        final @Nullable Integer length = facts.contextLength();
        return length == null
                ? messages.get(MessageKey.SETTINGS_MODELS_FOUND_UNKNOWN)
                : messages.get(MessageKey.SETTINGS_MODELS_FOUND_READ, length);
    }

    private static String verdictToken(final String verdict) {
        return switch (verdict) {
            case ModelReport.UNKNOWN_MODEL -> "unknownModel";
            case ModelReport.UNKNOWN_FINE_TUNE -> "unknownFineTune";
            case "recommended" -> "recommended";
            case "usable" -> "usable";
            case "experimental" -> "experimental";
            case "not recommended" -> "notRecommended";
            default -> "other";
        };
    }
}
