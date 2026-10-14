package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.util.lang.Script;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Reads one suggestion reply. Only a suggestion for a term of the batch survives, and only a target a glossary can hold:
 * one line, no placeholder token, at most {@value #MAX_TARGET_CHARS} characters — and, when the names are spelled in
 * another script, a target with no letter left in the source's script, since a model that copies the name has not
 * suggested anything. A target also has every letter in the target language's script, whatever the name policy.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionReplies {

    /** The longest target kept; the schema caps it at the same length. */
    static final int MAX_TARGET_CHARS = 80;

    /**
     * The model's suggestion for one entry. The reply's gender is not read: a small model answered "neuter" for every
     * place and thing (gemma4:e4b, 2026-10-02) and a person's gender from one example sentence (15h.A2), and the
     * gender is the review's, decided from evidence across the book.
     *
     * @param entry the entry as it was held when the batch was asked
     * @param target the suggested rendering, stripped and never blank
     * @param basis what is known of the entry that decides whether its rendering is locked as it is written
     * @param namesInflect whether the target language declines names, which the lock rule weighs
     */
    record Suggestion(GlossaryEntry entry, String target, LockRule.Basis basis, boolean namesInflect) {

        private static final LockRule.Basis UNKNOWN = new LockRule.Basis(false, false, false, false, false);

        /** A suggestion nothing is known of beyond its entry, so it is never locked. */
        Suggestion(final GlossaryEntry entry, final String target) {
            this(entry, target, UNKNOWN, true);
        }

        /** This suggestion under the facts the lock rule weighs, its entry and target unchanged. */
        Suggestion locking(final LockRule.Basis facts, final boolean inflects) {
            return new Suggestion(entry, target, facts, inflects);
        }

        /**
         * The entry as it is now with this suggestion written in as a suggested target, locked when the lock rule
         * says the rendering is final (a brand or a kept name as its source, a verified name where names are not
         * declined); nothing else changes.
         */
        GlossaryEntry onto(final GlossaryEntry now) {
            final GlossaryEntry suggested = now.withSuggestedTarget(target);
            return LockRule.locks(now.term(), target, basis, namesInflect) ? suggested.withRuleLock() : suggested;
        }
    }

    /** How a rendering stands before the script rules are applied to it. */
    enum Standing {
        /** The rendering is right as written, whatever its script: a brand or an identifier kept verbatim. */
        VERBATIM,
        /** The script rules decide. */
        OPEN,
        /** A copy of the source across scripts: refused and asked once more. */
        COPY,
        /** It does not open as the name transliterates: refused and asked once more. */
        LETTERS
    }

    /**
     * What one reply held.
     *
     * @param kept the suggestions that stand
     * @param refused the renderings refused for a copy or for the letter table, each with the entry it was for; the
     *     caller asks about them once more
     */
    record Replies(List<Suggestion> kept, List<Refusal> refused) {}

    /**
     * A rendering the reply gave and the rules refused.
     *
     * @param entry the entry as it was held when the batch was asked
     * @param rendering the refused rendering
     * @param why {@link Standing#COPY} or {@link Standing#LETTERS}
     */
    record Refusal(GlossaryEntry entry, String rendering, Standing why) {}

    /**
     * Reads a reply against the batch it answers.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param replyText the model's reply
     * @param batch the batch's entries by their {@link GlossaryKeys} key
     * @param sourceScript the script the names are written in, when a target in that script must be refused; null
     *     when any script is acceptable
     * @param targetScript the script the target language is written in, when every letter of a target must belong to
     *     it (a model sometimes answers in Arabic or copies the Latin name); null when it is not known
     * @return the suggestions kept; empty when the reply holds none or is unreadable
     */
    static List<Suggestion> read(
            final ObjectMapper mapper,
            final String replyText,
            final Map<String, GlossaryEntry> batch,
            @Nullable final Script sourceScript,
            @Nullable final Script targetScript) {
        return read(mapper, replyText, batch, sourceScript, targetScript, (_, _) -> Standing.OPEN)
                .kept();
    }

    /**
     * Reads a reply against the batch it answers, and sets aside the renderings the rules refuse for a retry.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param replyText the model's reply
     * @param batch the batch's entries by their {@link GlossaryKeys} key
     * @param sourceScript the script a target must not hold a letter of; null when any script is acceptable
     * @param targetScript the script every letter of a target must belong to; null when it is not known
     * @param standing how a usable rendering stands for its entry: a {@link Standing#VERBATIM} one skips the script
     *     rules, a refused one is returned in {@link Replies#refused()}
     * @return the suggestions kept and the renderings refused; both empty when the reply is unreadable
     */
    static Replies read(
            final ObjectMapper mapper,
            final String replyText,
            final Map<String, GlossaryEntry> batch,
            @Nullable final Script sourceScript,
            @Nullable final Script targetScript,
            final BiFunction<GlossaryEntry, String, Standing> standing) {
        final JsonNode suggestions = JsonReplies.tolerant(mapper, replyText)
                .map(root -> root.path("suggestions"))
                .orElse(null);
        if (suggestions == null || !suggestions.isArray()) {
            log.debug("Suggestion reply holds no suggestions array; read as no suggestions");
            return new Replies(List.of(), List.of());
        }
        final List<Suggestion> kept = new ArrayList<>();
        final List<Refusal> refused = new ArrayList<>();
        for (final JsonNode node : suggestions) {
            final Optional<Suggestion> read = suggestionOf(node, batch, sourceScript, targetScript, standing, refused);
            read.ifPresent(kept::add);
        }
        log.debug(
                "Suggestion reply read: {} kept, {} refused for a retry, of {}",
                kept.size(),
                refused.size(),
                suggestions.size());
        return new Replies(kept, refused);
    }

    private static Optional<Suggestion> suggestionOf(
            final JsonNode node,
            final Map<String, GlossaryEntry> batch,
            @Nullable final Script sourceScript,
            @Nullable final Script targetScript,
            final BiFunction<GlossaryEntry, String, Standing> standing,
            final List<Refusal> refused) {
        final String term = node.path("term").asText("");
        final GlossaryEntry entry = batch.get(GlossaryKeys.of(term));
        if (entry == null) {
            log.debug("Suggestion dropped: not a term of the batch");
            return Optional.empty();
        }
        final String target = LookAlikes.repaired(node.path("target").asText("").strip());
        final String shape = shapeRefusal(target);
        final Standing stands = shape != null ? Standing.OPEN : standing.apply(entry, target);
        if (stands == Standing.COPY || stands == Standing.LETTERS) {
            log.debug("Suggestion refused for a retry: {}", stands);
            log.trace("Suggestion refused for term '{}' target '{}': {}", term, target, stands);
            refused.add(new Refusal(entry, target, stands));
            return Optional.empty();
        }
        final String refusal = stands == Standing.VERBATIM ? shape : refusal(target, sourceScript, targetScript);
        if (refusal != null) {
            log.debug("Suggestion dropped: {}", refusal);
            log.trace("Suggestion dropped for term '{}' target '{}': {}", term, target, refusal);
            return Optional.empty();
        }
        return Optional.of(new Suggestion(entry, target));
    }

    // What no glossary can hold, whatever the script.
    private static @Nullable String shapeRefusal(final String target) {
        if (target.isEmpty()) {
            return "no suggestion";
        }
        if (target.indexOf('\n') >= 0 || target.indexOf('\r') >= 0) {
            return "more than one line";
        }
        if (target.indexOf('⟦') >= 0 || target.indexOf('⟧') >= 0) {
            return "holds a placeholder token";
        }
        return target.length() > MAX_TARGET_CHARS ? "longer than " + MAX_TARGET_CHARS + " characters" : null;
    }

    private static @Nullable String refusal(
            final String target, @Nullable final Script sourceScript, @Nullable final Script targetScript) {
        final String shape = shapeRefusal(target);
        if (shape != null) {
            return shape;
        }
        if (sourceScript != null && hasLetterIn(target, sourceScript)) {
            return "a letter still in the source's script";
        }
        return targetScript != null && hasLetterOutside(target, targetScript)
                ? "a letter outside the target language's script"
                : null;
    }

    private static boolean hasLetterOutside(final String target, final Script script) {
        return target.codePoints()
                .filter(Character::isLetter)
                .anyMatch(codePoint -> !script.letterScripts().contains(Character.UnicodeScript.of(codePoint)));
    }

    /**
     * Whether any of the target's letters is in the given script: a copied name, or a small model's look-alike Latin
     * letter inside a Cyrillic word ("Вeнс"), which reads right and breaks every search and match.
     */
    private static boolean hasLetterIn(final String target, final Script script) {
        return target.codePoints()
                .filter(Character::isLetter)
                .anyMatch(codePoint -> script.letterScripts().contains(Character.UnicodeScript.of(codePoint)));
    }
}
