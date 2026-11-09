package ua.bookloom.pipeline.proofread;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.glossary.NameForms;

/**
 * Replaces an entry's old target with its new one in a text, by code (design D15): a locked target is written exactly
 * as the person set it, so only its whole word is replaced; a pinned target is declined, so each generated form of the
 * old target is replaced by the same form of the new one. A form is paired by the ending it puts on the old target's
 * stem, and only when that ending on the new target gives one of the new target's own generated forms; a form with no
 * such counterpart is left alone rather than guessed. The term sweep's pinned path (task 13.4) is meant to reuse it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TargetSwap {

    /**
     * The text with the old target replaced.
     *
     * @param text the non-null text, such as a kept chapter summary
     * @param before the non-null entry before the change, whose target is not blank
     * @param after the non-null entry after the change, pinned or locked, whose target is not blank
     * @param language the target language tag, whose name table gives a pinned target's forms; null when unknown, so
     *     only the whole target is replaced
     * @return the text; unchanged when it holds no old form
     */
    static String swap(
            final String text, final GlossaryEntry before, final GlossaryEntry after, @Nullable final String language) {
        Objects.requireNonNull(text, "text");
        final Map<String, String> pairs = pairs(before, after, language);
        final Matcher matcher = WholeWord.anyOf(pairs.keySet()).matcher(text);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(Objects.requireNonNull(pairs.get(match.group()))));
    }

    // The whole target first, so a form that equals it never pairs it with anything else.
    private static Map<String, String> pairs(
            final GlossaryEntry before, final GlossaryEntry after, @Nullable final String language) {
        final String old = Objects.requireNonNull(before.target(), "before.target");
        final String renamed = Objects.requireNonNull(after.target(), "after.target");
        final Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put(old, renamed);
        if (after.locked()) {
            log.debug("Target swap entry={} locked: the whole word only", after.id());
            return pairs;
        }
        final Set<String> renamedForms =
                NameForms.forms(renamed, after.gender(), language).all();
        for (final String form : NameForms.forms(old, before.gender(), language).all()) {
            counterpart(old, form, renamed)
                    .filter(renamedForms::contains)
                    .ifPresent(paired -> pairs.putIfAbsent(form, paired));
        }
        log.debug("Target swap entry={} pinned language={} pairedForms={}", after.id(), language, pairs.size());
        if (log.isTraceEnabled()) {
            log.trace("Target swap entry={} pairs={}", after.id(), pairs);
        }
        return pairs;
    }

    // The form is the old target's stem plus an ending: the new target with the same ending in place of its own.
    private static Optional<String> counterpart(final String old, final String form, final String renamed) {
        int shared = 0;
        while (shared < old.length() && shared < form.length() && old.charAt(shared) == form.charAt(shared)) {
            shared++;
        }
        final String dropped = old.substring(shared);
        if (!renamed.endsWith(dropped)) {
            return Optional.empty();
        }
        return Optional.of(renamed.substring(0, renamed.length() - dropped.length()) + form.substring(shared));
    }
}
