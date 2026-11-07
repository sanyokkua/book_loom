package ua.bookloom.pipeline.prompt;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Block 12 of a proofread call (design D15 "Call"): the target language's minimal-edit instruction and at most five
 * rules, from its language file, or the English default of {@code generic.properties} for a target with none. A target
 * file's instruction names the source in its own genitive ({@code languageGenitive.<tag>}); a source it has no genitive
 * for gets the English default, so no prompt names a language in a wrong form, and a book whose source nobody chose
 * gets the instruction that names no source.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProofreadInstruction {

    /** The most rule lines block 12 carries. */
    static final int MAX_RULES = 5;

    private static final String INSTRUCTION = "proofreadInstruction";
    private static final String NO_SOURCE = ".noSource";
    private static final String RULES = "proofreadRules";
    private static final String GENITIVE = "languageGenitive.";
    private static final String GENITIVE_SLOT = "{{sourceLanguageGenitive}}";
    private static final String SOURCE_SLOT = "{{sourceLanguage}}";
    private static final String TARGET_SLOT = "{{targetLanguage}}";

    /**
     * The values of the proofread template's slots.
     *
     * @param instruction the instruction, never blank
     * @param rules the rule lines joined by a line break; empty for none
     */
    public record Block(String instruction, String rules) {}

    /**
     * Block 12's instruction and rules for one language pair.
     *
     * @param rules the non-null language-rules map the run's prompts read
     * @param sourceTag the source language tag, or null when nobody chose one
     * @param targetTag the non-null target language tag
     * @param prompt the non-null proofread prompt variant: P2 states the English default instruction for every target
     * @return the block; never null
     */
    public static Block of(
            final LanguageRules rules,
            @Nullable final String sourceTag,
            final String targetTag,
            final PromptVariants.ProofreadPrompt prompt) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(targetTag, "targetTag");
        Objects.requireNonNull(prompt, "prompt");
        final @Nullable String own = prompt == PromptVariants.ProofreadPrompt.P1 && !rules.genericOnly
                ? targetInstruction(rules, sourceTag, targetTag)
                : null;
        final String instruction = own != null ? own : englishInstruction(rules, sourceTag, targetTag);
        final String ruleLines = String.join("\n", ruleLines(rules, targetTag));
        log.debug(
                "Proofread instruction source={} target={} prompt={} own={} ruleLines={}",
                sourceTag,
                targetTag,
                prompt,
                own != null,
                ruleLines.isEmpty() ? 0 : ruleLines.split("\n").length);
        return new Block(instruction, ruleLines);
    }

    private static @Nullable String targetInstruction(
            final LanguageRules rules, @Nullable final String sourceTag, final String targetTag) {
        if (sourceTag == null) {
            return rules.targetValue(targetTag, INSTRUCTION + NO_SOURCE);
        }
        final @Nullable String text = rules.targetValue(targetTag, INSTRUCTION);
        final @Nullable String genitive = rules.targetValue(targetTag, GENITIVE + primary(sourceTag));
        if (text == null || (text.contains(GENITIVE_SLOT) && genitive == null)) {
            log.debug("No own proofread instruction for {} -> {}; the English default", sourceTag, targetTag);
            return null;
        }
        return genitive == null ? text : text.replace(GENITIVE_SLOT, genitive);
    }

    private static String englishInstruction(
            final LanguageRules rules, @Nullable final String sourceTag, final String targetTag) {
        final String text = rules.genericValue(sourceTag == null ? INSTRUCTION + NO_SOURCE : INSTRUCTION);
        final String named = text.replace(TARGET_SLOT, LanguageRules.name(targetTag));
        return sourceTag == null ? named : named.replace(SOURCE_SLOT, LanguageRules.name(sourceTag));
    }

    // The target's own rules, else the generic ones; never more than MAX_RULES lines.
    private static List<String> ruleLines(final LanguageRules rules, final String targetTag) {
        final @Nullable String own = rules.genericOnly ? null : rules.targetValue(targetTag, RULES);
        final String text = own != null ? own : rules.genericValue(RULES);
        final List<String> lines = Arrays.stream(text.split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.size() > MAX_RULES) {
            log.warn(
                    "The proofread rules of {} hold {} lines; the first {} are sent",
                    targetTag,
                    lines.size(),
                    MAX_RULES);
            return lines.subList(0, MAX_RULES);
        }
        return lines;
    }

    private static String primary(final String tag) {
        return Locale.forLanguageTag(tag.strip()).getLanguage();
    }
}
