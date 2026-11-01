package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * The forms of one word, grouped by the stem left once the source language's longest noun ending is removed, so a
 * recurring term the book writes in several cases ({@code фонарь}, {@code фонаря}, {@code фонарём}) is counted, read
 * and learned as one. Without a dictionary the stem is a guess, so a stem must keep at least {@link #MIN_STEM_LETTERS}
 * letters: a shorter one ({@code дом} of {@code дома}) would join unrelated words. A language whose file lists no
 * noun endings groups nothing.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FormGroups {

    /** The fewest letters a stem keeps once an ending is removed. */
    public static final int MIN_STEM_LETTERS = 4;

    /** The fewest uses, over the whole group, before a word is shown to the model as a recurring-term candidate. */
    public static final int MIN_GROUP_USES = 6;

    private static final String NOUN_ENDINGS = "nounEndings";

    /**
     * The forms of one word.
     *
     * @param key the stem the forms share, lower case
     * @param representative the form shown and stored as the term: the base form among them, else the most used
     * @param forms every form, lower case, with its uses; never empty
     */
    public record Group(String key, String representative, Map<String, Integer> forms) {

        /** Copies the forms. */
        public Group {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(representative, "representative");
            forms = Map.copyOf(forms);
        }

        /**
         * How often the book uses the word, in all its forms.
         *
         * @return the sum of the forms' uses
         */
        public int uses() {
            return forms.values().stream().mapToInt(Integer::intValue).sum();
        }

        /**
         * The forms other than the representative, most used first.
         *
         * @return never null; empty for a word met in one form
         */
        public List<String> others() {
            return forms.entrySet().stream()
                    .filter(form -> !form.getKey().equals(representative))
                    .sorted(Map.Entry.<String, Integer>comparingByValue()
                            .reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    /**
     * Groups the counted forms of some words by stem.
     *
     * @param counts the lower-case forms with their uses; never null
     * @param source the source language's BCP 47 tag, whose noun endings find the stems; null groups nothing
     * @return the groups in the order their first form was counted; never null
     */
    public static List<Group> of(final Map<String, Integer> counts, @Nullable final String source) {
        Objects.requireNonNull(counts, "counts");
        final List<String> nounEndings =
                source == null ? List.of() : LanguageRules.bundled().wordsOf(source, NOUN_ENDINGS);
        final List<String> obliqueEndings =
                source == null ? List.of() : LanguageRules.bundled().obliqueEndings(source);
        final Map<String, Map<String, Integer>> byKey = new LinkedHashMap<>();
        counts.forEach((form, uses) -> byKey.computeIfAbsent(keyOf(form, nounEndings), _ -> new LinkedHashMap<>())
                .merge(form.toLowerCase(Locale.ROOT), uses, Integer::sum));
        final List<Group> groups = new ArrayList<>();
        byKey.forEach((key, forms) -> groups.add(new Group(key, representativeOf(forms, obliqueEndings), forms)));
        log.debug(
                "form groups forms={} groups={} grouped={}",
                counts.size(),
                groups.size(),
                groups.stream().filter(group -> group.forms().size() > 1).count());
        if (log.isTraceEnabled()) {
            groups.stream()
                    .filter(group -> group.forms().size() > 1)
                    .forEach(group -> log.trace("form group {} = {}", group.representative(), group.forms()));
        }
        return List.copyOf(groups);
    }

    /**
     * The stem a form is grouped under.
     *
     * @param form the non-null form as written
     * @param nounEndings the source language's noun endings; never null, empty for none
     * @return the lower-cased form without its longest ending when the stem left keeps {@link #MIN_STEM_LETTERS}
     *     letters, else the whole lower-cased form
     */
    public static String keyOf(final String form, final List<String> nounEndings) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(nounEndings, "nounEndings");
        final String lower = form.strip().toLowerCase(Locale.ROOT);
        return nounEndings.stream()
                .filter(lower::endsWith)
                .max(Comparator.comparingInt(String::length))
                .map(ending -> lower.substring(0, lower.length() - ending.length()))
                .filter(stem -> stem.codePointCount(0, stem.length()) >= MIN_STEM_LETTERS)
                .orElse(lower);
    }

    private static String representativeOf(final Map<String, Integer> forms, final List<String> obliqueEndings) {
        return BaseForms.of(forms, obliqueEndings)
                .orElseGet(() -> forms.entrySet().stream()
                        .max(Map.Entry.<String, Integer>comparingByValue()
                                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                        .map(Map.Entry::getKey)
                        .orElseThrow());
    }
}
