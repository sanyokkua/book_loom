package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Reads one glossary-review reply. Only a verdict on a term of the batch survives, and a verdict word or type the
 * schema does not list reads as "no opinion", so an unreadable reply changes nothing.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermReviewReplies {

    /** What the model said a term is. */
    enum Kind {
        /** A proper name. */
        NAME,
        /** A domain term. */
        TERM,
        /** An ordinary word that is not a name. */
        NOT_A_NAME,
        /** No verdict the schema lists. */
        UNSURE
    }

    /**
     * The model's verdict on one held entry.
     *
     * @param entry the entry as it was held when the batch was asked
     * @param kind what the model said it is
     * @param type the type the model guessed; {@link TermType#OTHER} when none
     * @param gender the gender the model guessed; {@link Gender#UNKNOWN} when none
     */
    record Verdict(GlossaryEntry entry, Kind kind, TermType type, Gender gender) {}

    /**
     * Reads a reply against the batch it answers.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param replyText the model's reply
     * @param batch the batch's entries by their {@link GlossaryKeys} key
     * @return the verdicts on entries of the batch; empty when the reply holds none or is unreadable
     */
    static List<Verdict> read(
            final ObjectMapper mapper, final String replyText, final Map<String, GlossaryEntry> batch) {
        final JsonNode verdicts = JsonReplies.tolerant(mapper, replyText)
                .map(root -> root.path("verdicts"))
                .orElse(null);
        if (verdicts == null || !verdicts.isArray()) {
            log.debug("Glossary review reply holds no verdicts array; read as no verdicts");
            return List.of();
        }
        final List<Verdict> kept = new ArrayList<>();
        for (final JsonNode node : verdicts) {
            final GlossaryEntry entry =
                    batch.get(GlossaryKeys.of(node.path("term").asText("")));
            if (entry != null) {
                final Kind kind = kindOf(node.path("verdict").asText(""));
                kept.add(new Verdict(
                        entry,
                        kind,
                        typeOf(kind, node.path("type").asText("")),
                        genderOf(node.path("gender").asText(""))));
            }
        }
        log.debug("Glossary review reply read: {} verdicts kept of {}", kept.size(), verdicts.size());
        return kept;
    }

    private static Kind kindOf(final String wire) {
        return switch (GlossaryKeys.of(wire)) {
            case "name" -> Kind.NAME;
            case "term" -> Kind.TERM;
            case "not-a-name" -> Kind.NOT_A_NAME;
            default -> Kind.UNSURE;
        };
    }

    private static TermType typeOf(final Kind kind, final String wire) {
        return switch (GlossaryKeys.of(wire)) {
            case "person" -> TermType.CHARACTER;
            case "place" -> TermType.PLACE;
            case "term" -> TermType.TERM;
            case "title" -> TermType.TITLE;
            default -> kind == Kind.TERM ? TermType.TERM : TermType.OTHER;
        };
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
