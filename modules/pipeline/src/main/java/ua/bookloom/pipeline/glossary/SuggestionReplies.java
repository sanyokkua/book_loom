package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.util.lang.Script;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Reads one suggestion reply. Only a suggestion for a term of the batch survives, and only a target a glossary can hold:
 * one line, no placeholder token, at most {@value #MAX_TARGET_CHARS} characters — and, when the names are spelled in
 * another script, a target with no letter left in the source's script, since a model that copies the name has not
 * suggested anything.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionReplies {

    /** The longest target kept; the schema caps it at the same length. */
    static final int MAX_TARGET_CHARS = 80;

    /**
     * The model's suggestion for one entry.
     *
     * @param entry the entry as it was held when the batch was asked
     * @param target the suggested rendering, stripped and never blank
     * @param gender the gender the model gave; {@link Gender#UNKNOWN} when none
     */
    record Suggestion(GlossaryEntry entry, String target, Gender gender) {

        /**
         * The entry as it is now with this suggestion written in: the target as a suggestion, and the gender only for a
         * person whose gender is still unknown. A small model answered "neuter" for every place and thing, masculine
         * Ukrainian nouns included (gemma4:e4b, 2026-10-02), and a wrong gender misleads every draft's agreement more
         * than none, which the draft infers from its own rendering.
         */
        GlossaryEntry onto(final GlossaryEntry now) {
            final boolean takesGender = now.gender() == Gender.UNKNOWN && now.type() == TermType.CHARACTER;
            return now.withSuggestedTarget(target).withGender(takesGender ? gender : now.gender());
        }
    }

    /**
     * Reads a reply against the batch it answers.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param replyText the model's reply
     * @param batch the batch's entries by their {@link GlossaryKeys} key
     * @param sourceScript the script the names are written in, when a target in that script must be refused; null
     *     when any script is acceptable
     * @return the suggestions kept; empty when the reply holds none or is unreadable
     */
    static List<Suggestion> read(
            final ObjectMapper mapper,
            final String replyText,
            final Map<String, GlossaryEntry> batch,
            @Nullable final Script sourceScript) {
        final JsonNode suggestions = JsonReplies.tolerant(mapper, replyText)
                .map(root -> root.path("suggestions"))
                .orElse(null);
        if (suggestions == null || !suggestions.isArray()) {
            log.debug("Suggestion reply holds no suggestions array; read as no suggestions");
            return List.of();
        }
        final List<Suggestion> kept = new ArrayList<>();
        for (final JsonNode node : suggestions) {
            suggestionOf(node, batch, sourceScript).ifPresent(kept::add);
        }
        log.debug("Suggestion reply read: {} kept of {}", kept.size(), suggestions.size());
        return kept;
    }

    private static Optional<Suggestion> suggestionOf(
            final JsonNode node, final Map<String, GlossaryEntry> batch, @Nullable final Script sourceScript) {
        final String term = node.path("term").asText("");
        final GlossaryEntry entry = batch.get(GlossaryKeys.of(term));
        if (entry == null) {
            log.debug("Suggestion dropped: not a term of the batch");
            return Optional.empty();
        }
        final String target = LookAlikes.repaired(node.path("target").asText("").strip());
        final String refusal = refusal(target, sourceScript);
        if (refusal != null) {
            log.debug("Suggestion dropped: {}", refusal);
            log.trace("Suggestion dropped for term '{}' target '{}': {}", term, target, refusal);
            return Optional.empty();
        }
        return Optional.of(
                new Suggestion(entry, target, genderOf(node.path("gender").asText(""))));
    }

    private static @Nullable String refusal(final String target, @Nullable final Script sourceScript) {
        if (target.isEmpty()) {
            return "no suggestion";
        }
        if (target.indexOf('\n') >= 0 || target.indexOf('\r') >= 0) {
            return "more than one line";
        }
        if (target.indexOf('⟦') >= 0 || target.indexOf('⟧') >= 0) {
            return "holds a placeholder token";
        }
        if (target.length() > MAX_TARGET_CHARS) {
            return "longer than " + MAX_TARGET_CHARS + " characters";
        }
        return sourceScript != null && hasLetterIn(target, sourceScript)
                ? "a letter still in the source's script"
                : null;
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

    private static Gender genderOf(final String wire) {
        return switch (GlossaryKeys.of(wire)) {
            case "female" -> Gender.FEMALE;
            case "male" -> Gender.MALE;
            case "neuter" -> Gender.NEUTER;
            default -> Gender.UNKNOWN;
        };
    }
}
