package ua.bookloom.llm.client.openai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ReasoningLevel;

/**
 * Chooses OpenRouter's {@code reasoning} object from what the catalog said about the model, since one off value does
 * not fit every model: {@code none} only where it is listed, {@code enabled:false} where the model lists no efforts,
 * the lowest effort where reasoning cannot be turned off (OpenRouter refuses {@code none} there), and nothing for a
 * model the catalog gives no reasoning object.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenRouterReasoning {

    private static final String THINKING = "thinking";
    private static final String EFFORT = "effort";
    private static final String ENABLED = "enabled";
    private static final String NONE = "none";
    // OpenRouter's efforts from least to most reasoning.
    private static final List<String> EFFORT_ORDER = List.of(NONE, "minimal", "low", "medium", "high", "xhigh", "max");

    /**
     * The reasoning object of one call.
     *
     * @param facts what the catalog said about the model this session, or null when it was not read
     * @return the object, or null when none is sent
     */
    static @Nullable Map<String, Object> object(final @Nullable ModelFacts facts, final ReasoningLevel level) {
        if (facts == null || !facts.capabilities().contains(THINKING)) {
            log.debug("OpenRouter reasoning left out: the catalog declares none read={}", facts != null);
            return null;
        }
        final Map<String, Object> chosen = chosen(facts, level);
        log.debug(
                "OpenRouter reasoning level={} mandatory={} efforts={} sent={}",
                level,
                facts.reasoningMandatory(),
                facts.thinkingOptions(),
                chosen);
        return chosen;
    }

    private static @Nullable Map<String, Object> chosen(final ModelFacts facts, final ReasoningLevel level) {
        if (level == ReasoningLevel.ON) {
            return Map.of(ENABLED, true);
        }
        final List<String> efforts = facts.thinkingOptions().stream()
                .map(e -> e.toLowerCase(Locale.ROOT))
                .toList();
        if (facts.reasoningMandatory()) {
            final String lowest = lowest(efforts);
            return lowest == null ? null : Map.of(EFFORT, lowest);
        }
        return efforts.contains(NONE) ? Map.of(EFFORT, NONE) : Map.of(ENABLED, false);
    }

    private static @Nullable String lowest(final List<String> efforts) {
        return EFFORT_ORDER.stream()
                .filter(effort -> !NONE.equals(effort) && efforts.contains(effort))
                .findFirst()
                .orElse(null);
    }
}
