package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.pipeline.prompt.PromptName;
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

    /** The longest meaning note kept; the schema caps it at the same length, and a longer one is dropped. */
    static final int MAX_MEANING_CHARS = 80;

    /** How the rules judge one usable rendering of an entry. */
    interface Judge {

        /**
         * Where the rendering stands before the script rules.
         *
         * @param entry the entry the rendering is for
         * @param rendering the stripped, one-line rendering
         * @return the standing; a refusing one is asked once more
         */
        Standing standing(GlossaryEntry entry, String rendering);

        /**
         * Why the target language's rules refuse a rendering that stands open.
         *
         * @param entry the entry the rendering is for
         * @param rendering the stripped, one-line rendering
         * @return the reason in the words the retry's line quotes, or null when the rules accept it
         */
        @Nullable
        String suggestionRefusal(GlossaryEntry entry, String rendering);
    }

    /** A judge that leaves every rendering to the script rules. */
    private static final Judge OPEN = new Judge() {
        @Override
        public Standing standing(final GlossaryEntry entry, final String rendering) {
            return Standing.OPEN;
        }

        @Override
        public @Nullable String suggestionRefusal(final GlossaryEntry entry, final String rendering) {
            return null;
        }
    };

    /**
     * The model's suggestion for one entry. The reply's gender is not read: a small model answered "neuter" for every
     * place and thing (gemma4:e4b, 2026-10-02) and a person's gender from one example sentence (15h.A2), and the
     * gender is the review's, decided from evidence across the book.
     *
     * @param entry the entry as it was held when the batch was asked
     * @param target the suggested rendering, stripped and never blank
     * @param meaning what the words of a name that was sounded out mean, kept as the entry's note; null when the reply
     *     gave none or the entry takes no note
     * @param basis what is known of the entry that decides whether its rendering is locked as it is written
     * @param namesInflect whether the target language declines names, which the lock rule weighs
     */
    record Suggestion(
            GlossaryEntry entry, String target, @Nullable String meaning, LockRule.Basis basis, boolean namesInflect) {

        private static final LockRule.Basis UNKNOWN = new LockRule.Basis(false, false, false, false);

        /** A suggestion nothing is known of beyond its entry, so it is never locked. */
        Suggestion(final GlossaryEntry entry, final String target) {
            this(entry, target, null);
        }

        /** A suggestion with the meaning the reply gave, never locked. */
        Suggestion(final GlossaryEntry entry, final String target, @Nullable final String meaning) {
            this(entry, target, meaning, UNKNOWN, true);
        }

        /** This suggestion under the facts the lock rule weighs, its entry, target and meaning unchanged. */
        Suggestion locking(final LockRule.Basis facts, final boolean inflects) {
            return new Suggestion(entry, target, meaning, facts, inflects);
        }

        /** This suggestion with another target (a cluster's agreed spelling), the rest unchanged. */
        Suggestion retargeted(final String agreed) {
            return new Suggestion(entry, agreed, meaning, basis, namesInflect);
        }

        /** This suggestion without a meaning note. */
        Suggestion withoutMeaning() {
            return meaning == null ? this : new Suggestion(entry, target, null, basis, namesInflect);
        }

        /**
         * The entry as it is now with this suggestion written in as a suggested target, locked when the lock rule
         * says the rendering is final (a kept name or brand as its source, a verified name where names are not
         * declined), and with the meaning as its note unless it already holds one, which may be the person's.
         */
        GlossaryEntry onto(final GlossaryEntry now) {
            final GlossaryEntry noted = meaning != null && now.meaning() == null ? now.withMeaning(meaning) : now;
            final GlossaryEntry suggested = noted.withSuggestedTarget(target);
            return LockRule.locks(now.term(), target, basis, namesInflect) ? suggested.withRuleLock() : suggested;
        }
    }

    /** How a rendering stands before the script rules are applied to it. */
    enum Standing {
        /** The rendering is right as written, whatever its script: an identifier kept verbatim. */
        VERBATIM(""),
        /** The script rules decide. */
        OPEN(""),
        /** A copy of the source across scripts: refused and asked once more. */
        COPY("it is the source spelling, not a rendering"),
        /** It does not open as the name transliterates: refused and asked once more. */
        LETTERS("it does not begin the way the name sounds"),
        /** It leaves out a capitalised word of the name: refused and asked once more. */
        LOST_WORD("it leaves out a word of the name"),
        /** It holds a letter the target language does not use: refused and asked once more. */
        ALPHABET("it has a letter the target language does not use"),
        /** It is a case form of a name that ends in a consonant, not the dictionary form: refused, asked once more. */
        OBLIQUE("it is a case form, not the dictionary form"),
        /** It changes an identifier, which stays as written: refused and asked once more. */
        IDENTIFIER("it is an identifier and stays as written");

        private final String reason;

        Standing(final String reason) {
            this.reason = reason;
        }

        /** Why the rendering was refused, as the retry's line says it; empty for a standing that is no refusal. */
        String reason() {
            return reason;
        }

        boolean isRefusal() {
            return !reason.isEmpty();
        }
    }

    /**
     * What one reply held.
     *
     * @param kept the suggestions that stand
     * @param refused the renderings refused by the name rules, each with the entry it was for; the
     *     caller asks about them once more
     */
    record Replies(List<Suggestion> kept, List<Refusal> refused) {

        /** The refusals by entry id, leaving out an entry the same reply also answered acceptably. */
        Map<String, Refusal> refusedNotAnswered() {
            final Set<String> answered = new HashSet<>();
            kept.forEach(suggestion -> answered.add(suggestion.entry().id()));
            final Map<String, Refusal> open = new LinkedHashMap<>();
            refused.stream()
                    .filter(refusal -> !answered.contains(refusal.entry().id()))
                    .forEach(refusal -> open.putIfAbsent(refusal.entry().id(), refusal));
            return open;
        }
    }

    /**
     * A rendering the reply gave and the rules refused.
     *
     * @param entry the entry as it was held when the batch was asked
     * @param rendering the refused rendering
     * @param why why it was refused, in the words the retry's line quotes
     */
    record Refusal(GlossaryEntry entry, String rendering, String why) {}

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
        return read(mapper, replyText, batch, sourceScript, targetScript, OPEN, UnaryOperator.identity())
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
     * @param judge how a usable rendering stands for its entry: a {@link Standing#VERBATIM} one skips the script
     *     rules, and a refusing standing or a refusal of the target language's rules is returned in
     *     {@link Replies#refused()}
     * @param cleaner what a target is stored as before any rule reads it (its stress marks removed where the target
     *     language asks for that)
     * @return the suggestions kept and the renderings refused; both empty when the reply is unreadable
     */
    static Replies read(
            final ObjectMapper mapper,
            final String replyText,
            final Map<String, GlossaryEntry> batch,
            @Nullable final Script sourceScript,
            @Nullable final Script targetScript,
            final Judge judge,
            final UnaryOperator<String> cleaner) {
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
            final Optional<Suggestion> read =
                    suggestionOf(node, batch, new Rules(sourceScript, targetScript, judge, cleaner), refused);
            read.ifPresent(kept::add);
        }
        log.debug(
                "Suggestion reply read: {} kept, {} refused for a retry, of {}",
                kept.size(),
                refused.size(),
                suggestions.size());
        return new Replies(kept, refused);
    }

    /** The rules one reply is read by. */
    private record Rules(
            @Nullable Script sourceScript, @Nullable Script targetScript, Judge judge, UnaryOperator<String> cleaner) {}

    private static Optional<Suggestion> suggestionOf(
            final JsonNode node,
            final Map<String, GlossaryEntry> batch,
            final Rules rules,
            final List<Refusal> refused) {
        final String term = node.path("term").asText("");
        final GlossaryEntry entry = batch.get(GlossaryKeys.of(term));
        if (entry == null) {
            log.debug("Suggestion dropped: not a term of the batch");
            return Optional.empty();
        }
        final String target = rules.cleaner()
                .apply(LookAlikes.repaired(node.path("target").asText("").strip()));
        final String shape = shapeRefusal(target);
        final Standing stands = shape != null ? Standing.OPEN : rules.judge().standing(entry, target);
        final String why = stands.isRefusal() ? stands.reason() : ruleRefusal(entry, target, shape, stands, rules);
        if (why != null) {
            log.debug("Suggestion refused for a retry: standing={} rules={}", stands, why);
            log.trace("Suggestion refused for term '{}' target '{}': {}", term, target, why);
            refused.add(new Refusal(entry, target, why));
            return Optional.empty();
        }
        final String refusal =
                stands == Standing.VERBATIM ? shape : refusal(target, rules.sourceScript(), rules.targetScript());
        if (refusal != null) {
            log.debug("Suggestion dropped: {}", refusal);
            log.trace("Suggestion dropped for term '{}' target '{}': {}", term, target, refusal);
            return Optional.empty();
        }
        return Optional.of(new Suggestion(entry, target, meaningOf(node)));
    }

    // The target language's rules judge only a rendering that is well formed and stands open; an identifier kept as
    // written and a shape no glossary holds are past them.
    private static @Nullable String ruleRefusal(
            final GlossaryEntry entry,
            final String target,
            @Nullable final String shape,
            final Standing stands,
            final Rules rules) {
        return shape == null && stands == Standing.OPEN ? rules.judge().suggestionRefusal(entry, target) : null;
    }

    // A meaning note is one short line; anything else is no note, and the suggestion stands without one.
    private static @Nullable String meaningOf(final JsonNode node) {
        final String meaning = node.path("meaning").asText("").strip();
        final boolean isNote = !meaning.isEmpty()
                && meaning.length() <= MAX_MEANING_CHARS
                && meaning.indexOf('\n') < 0
                && meaning.indexOf('\r') < 0;
        return isNote ? meaning : null;
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

    /** The batch's terms as the call describes them before the model answers. */
    static CallDescriptor asking(final List<GlossaryEntry> batch) {
        return CallDescriptor.asking(
                PromptName.SUGGEST_TARGETS,
                batch.stream().map(GlossaryEntry::term).toList());
    }

    /** What a person reads of a batch: each term asked, with the target the reply gave it, if any. */
    static Map<String, @Nullable String> shown(final List<GlossaryEntry> batch, final List<Suggestion> kept) {
        final Map<String, @Nullable String> shown = new LinkedHashMap<>();
        batch.forEach(entry -> shown.put(entry.term(), null));
        kept.forEach(suggestion -> shown.put(suggestion.entry().term(), suggestion.target()));
        return shown;
    }
}
