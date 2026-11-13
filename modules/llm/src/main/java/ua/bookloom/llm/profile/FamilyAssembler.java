package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Reads family, generation and line out of the words no other rule claimed ("qwen3.5" is qwen and 3.5), and splits off
 * a fine-tune's label and the family a distillation learned from.
 */
@Slf4j
final class FamilyAssembler {

    private static final Pattern PLAIN_WORD = Pattern.compile("[a-z][a-z0-9.]*");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d+");

    /** Mutable state of one assembly. */
    private static final class Parts {
        final List<String> family = new ArrayList<>();
        final List<String> finetune = new ArrayList<>();
        final List<String> distillOf = new ArrayList<>();

        @Nullable
        String version;

        @Nullable
        String line;
    }

    FamilyWords assemble(final List<PendingWord> pending, final int distillIndex) {
        final Parts parts = new Parts();
        for (int i = 0; i < pending.size(); i++) {
            if (distillIndex >= 0 && i >= distillIndex) {
                distilled(pending.get(i).text(), parts);
            } else {
                place(pending, i, parts);
            }
        }
        String joined = parts.family.isEmpty() ? null : String.join("-", parts.family);
        if (joined != null && !parts.distillOf.isEmpty()) {
            joined += "-distill";
        }
        final String family = joined == null ? null : NameVocabulary.FAMILY_ALIAS.getOrDefault(joined, joined);
        log.debug("Family pending={} family={} version={} line={}", pending, family, parts.version, parts.line);
        return new FamilyWords(
                family,
                parts.version,
                parts.line,
                parts.finetune.isEmpty() ? null : String.join("-", parts.finetune),
                parts.distillOf.isEmpty() ? null : String.join("-", parts.distillOf));
    }

    private static void distilled(final String word, final Parts parts) {
        if (parts.distillOf.isEmpty() && NameVocabulary.DISTILL_BASES.contains(word)) {
            parts.distillOf.add(word);
        } else if (!parts.distillOf.isEmpty() && PLAIN_WORD.matcher(word).matches()) {
            parts.distillOf.add(word);
        } else {
            parts.finetune.add(word);
        }
    }

    private static void place(final List<PendingWord> pending, final int index, final Parts parts) {
        final PendingWord word = pending.get(index);
        final String text = word.text();
        final Matcher glued = NameVocabulary.VERSION_GLUED.matcher(text);
        final boolean isLine = NameVocabulary.LINES.contains(text);
        if (parts.version == null
                && !parts.family.isEmpty()
                && NameVocabulary.VERSION_ONLY.matcher(text).matches()) {
            parts.version = text;
        } else if (isMinorDigit(pending, index, parts)) {
            parts.version = parts.version + "." + text;
        } else if (parts.version == null && !isLine && glued.matches()) {
            addGlued(glued, text, parts);
        } else if (parts.version != null && isLine && parts.line == null) {
            parts.line = text;
        } else if (parts.version == null && isLine && !parts.family.isEmpty() && !"omni".equals(text)) {
            parts.family.add(text);
        } else if (word.afterSize() && !parts.family.isEmpty()) {
            parts.finetune.add(text);
        } else {
            parts.family.add(text);
        }
    }

    /** A hyphenated minor version: "mistral-medium-3-5" is generation 3.5. */
    private static boolean isMinorDigit(final List<PendingWord> pending, final int index, final Parts parts) {
        final PendingWord word = pending.get(index);
        return parts.version != null
                && index > 0
                && DIGIT.matcher(word.text()).matches()
                && WHOLE_NUMBER.matcher(parts.version).matches()
                && pending.get(index - 1).text().equals(parts.version)
                && !word.afterSize();
    }

    private static void addGlued(final Matcher glued, final String text, final Parts parts) {
        if (glued.group(1).length() == 1 && !parts.family.isEmpty()) {
            parts.family.add(text);
        } else {
            parts.family.add(glued.group(1));
            parts.version = glued.group(2);
        }
    }
}
