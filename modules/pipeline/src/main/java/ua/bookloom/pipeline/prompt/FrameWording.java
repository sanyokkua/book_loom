package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * What the context builder's fixed frame reads from the language files (design D9): the book rules' wording, the
 * reminder lines, the pair's own examples, the target's illustration and the language-rules section without the rules
 * a book-rule line already states. Kept apart from {@link LanguageRules}, which today's prompts read, so that map stays
 * as it is until the old render sites go (task 11.8).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FrameWording {

    /** The keys the generic file must state for the frame; checked when the map loads, as its own keys are. */
    static final List<String> GENERIC_KEYS = List.of(
            "rule.marks",
            "rule.dialogue",
            "rule.address",
            "rule.numbers",
            "rule.inflect",
            "reminder.marks",
            "reminder.dialogue",
            "reminder.narrator",
            "illustration.label");

    private static final String BOOK_RULE = "rule.";
    private static final String EXAMPLE_SEPARATOR = "\n\n";
    private static final String LINE_START = "- ";

    /**
     * The wording of one book rule for a target language: the target file's {@code rule.<key>}, else its plain
     * {@code <key>} rule, else the generic file's, so a rule is said in the target's own terms where it has them.
     *
     * @param rules the non-null language-rules map
     * @param targetTag the non-null target language tag
     * @param key the non-null rule key, such as {@code dialogue} or {@code inflect}
     * @return the wording, or empty when no file states the rule; never null
     */
    public static String bookRule(final LanguageRules rules, final String targetTag, final String key) {
        Objects.requireNonNull(targetTag, "targetTag");
        Objects.requireNonNull(key, "key");
        final List<LanguageFile> order =
                rules.genericOnly ? List.of(rules.generic) : List.of(rules.languageFile(targetTag), rules.generic);
        for (final LanguageFile file : order) {
            final String own = Objects.requireNonNullElse(file.get(BOOK_RULE + key), "");
            final String found = own.isEmpty() ? Objects.requireNonNullElse(file.get(key), "") : own;
            if (!found.isEmpty()) {
                log.debug("Book rule {} for {} from {}", key, targetTag, file.name());
                return found;
            }
        }
        log.debug("Book rule {} for {}: no file states it", key, targetTag);
        return "";
    }

    /**
     * A line of fixed wording the target file may state in its own terms, such as a reminder or a label: the target
     * file's value, else the generic one.
     *
     * @param rules the non-null language-rules map
     * @param targetTag the non-null target language tag
     * @param key the non-null key, such as {@code reminder.marks}
     * @return the wording, or empty when neither file states it; never null
     */
    public static String wording(final LanguageRules rules, final String targetTag, final String key) {
        Objects.requireNonNull(targetTag, "targetTag");
        Objects.requireNonNull(key, "key");
        final String own = rules.genericOnly ? null : rules.targetValue(targetTag, key);
        return own != null ? own : Objects.requireNonNullElse(rules.generic.get(key), "");
    }

    /**
     * The pair file's own worked examples in the batch reply's shape (its {@code batchExample.N}, the form every
     * translate-family reply of the context builder takes), with no fallback: a pair without them shows the target's
     * illustration.
     *
     * @param rules the non-null language-rules map
     * @param sourceTag the source language tag, or null when it is inferred, which has no pair
     * @param targetTag the non-null target language tag
     * @return the examples as the prompt shows them, or empty when the pair has none; never null
     */
    public static String pairExamples(
            final LanguageRules rules, @Nullable final String sourceTag, final String targetTag) {
        Objects.requireNonNull(targetTag, "targetTag");
        if (sourceTag == null || rules.genericOnly) {
            return "";
        }
        final List<String> raw = rules.pairFile(sourceTag, targetTag).indexed("batchExample");
        return raw.isEmpty() ? "" : BatchExamples.render(String.join(EXAMPLE_SEPARATOR, raw));
    }

    /**
     * A few lines of the target language's own prose, from its {@code illustration} key, shown to set the style when
     * the pair has no examples.
     *
     * @param rules the non-null language-rules map
     * @param targetTag the non-null target language tag
     * @return the illustration, or empty when the language has none; never null
     */
    public static String illustration(final LanguageRules rules, final String targetTag) {
        Objects.requireNonNull(targetTag, "targetTag");
        final String value = rules.genericOnly ? null : rules.targetValue(targetTag, "illustration");
        return value == null ? "" : value;
    }

    /**
     * The language-rules section of a drafting call without the rules a book-rule line states, so none is said twice.
     *
     * @param rules the non-null language-rules map
     * @param sourceTag the source language tag, or null when it is inferred
     * @param targetTag the non-null target language tag
     * @param excluded the rules left out of every part; never null, may be empty
     * @return the section, or empty when the pair has none; never null
     */
    public static String section(
            final LanguageRules rules,
            @Nullable final String sourceTag,
            final String targetTag,
            final Set<RuleKey> excluded) {
        Objects.requireNonNull(excluded, "excluded");
        final Set<String> starts =
                excluded.stream().map(rule -> LINE_START + rule.label() + ": ").collect(Collectors.toSet());
        final List<String> kept = rules.section(sourceTag, targetTag, false)
                .lines()
                .filter(line -> starts.stream().noneMatch(line::startsWith))
                .toList();
        return String.join("\n", withoutEmptyParts(kept));
    }

    // A part heading ("Target — …", the section's own "[…]") whose every rule was left out goes too.
    private static List<String> withoutEmptyParts(final List<String> lines) {
        final List<String> kept = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            final boolean isRule = lines.get(index).startsWith(LINE_START);
            final boolean ruleFollows =
                    lines.subList(index + 1, lines.size()).stream().anyMatch(line -> line.startsWith(LINE_START));
            final boolean nextIsRule =
                    index + 1 < lines.size() && lines.get(index + 1).startsWith(LINE_START);
            if (isRule || (index == 0 ? ruleFollows : nextIsRule)) {
                kept.add(lines.get(index));
            }
        }
        return kept;
    }

    /**
     * A language's English name, as every section heading names it.
     *
     * @param tag the non-null language tag
     * @return the display name, or the tag's description when the JDK has none
     */
    public static String englishName(final String tag) {
        return LanguageRules.name(Objects.requireNonNull(tag, "tag"));
    }
}
