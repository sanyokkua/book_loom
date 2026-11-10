package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.RefusedSetting;
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
    private static final String SCHEMA_SETTING = "response_format";
    private static final List<String> DEFAULT_KINDS = List.of("draft", "term-reading");
    private static final String SCHEMA = "schema";
    private static final String PLAIN_JSON = "plainJson";
    private static final String UNCHECKED = "unchecked";

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

    /**
     * How the model answers each call kind that asks for a structured answer: the schema, or plain JSON.
     *
     * @param facts what the program says about the model; its session answer is the last inference test's
     * @param refused what the model refused, whose answer-schema refusals name the kinds that fell back
     * @return one line per kind, Translate and Name reading first; one line saying nothing was checked when no kind is
     *     known; never empty
     */
    public List<String> structured(final ModelFacts facts, final List<RefusedSetting> refused) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(refused, "refused");
        final List<String> kinds = new ArrayList<>(DEFAULT_KINDS);
        refused.stream()
                .filter(setting -> SCHEMA_SETTING.equals(setting.name()) && setting.scope() != null)
                .map(RefusedSetting::scope)
                .filter(scope -> !kinds.contains(scope))
                .forEach(kinds::add);
        final List<String> lines = new ArrayList<>();
        boolean anyKnown = false;
        for (final String kind : kinds) {
            final String answer = answerFor(kind, facts, refused).orElse(UNCHECKED);
            anyKnown |= !UNCHECKED.equals(answer);
            lines.add(messages.get(
                    MessageKey.SETTINGS_MODELS_FOUND_STRUCTURED_ROW,
                    messages.get(MessageKey.SETTINGS_MODELS_FOUND_STRUCTURED_KIND, kind.replace('-', '_'), kind),
                    answer));
        }
        log.debug("structured rows: {} kind(s), any known {}", kinds.size(), anyKnown);
        return anyKnown ? lines : List.of(messages.get(MessageKey.SETTINGS_MODELS_FOUND_UNCHECKED));
    }

    private static Optional<String> answerFor(
            final String kind, final ModelFacts facts, final List<RefusedSetting> refused) {
        final boolean fellBack = refused.stream()
                .anyMatch(setting -> SCHEMA_SETTING.equals(setting.name())
                        && (setting.scope() == null || kind.equals(setting.scope())));
        if (fellBack) {
            return Optional.of(PLAIN_JSON);
        }
        final ProbeVerdicts.Verdict checked = facts.verdicts().answerSchema();
        if (Boolean.TRUE.equals(facts.structured())
                || (checked != null && checked.outcome() == ProbeVerdicts.Outcome.HONOURED)) {
            return Optional.of(SCHEMA);
        }
        return checked == null && facts.structured() == null ? Optional.empty() : Optional.of(PLAIN_JSON);
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
