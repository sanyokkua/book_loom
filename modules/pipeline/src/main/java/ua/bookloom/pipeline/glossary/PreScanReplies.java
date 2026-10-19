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
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Reads one pre-scan reply into proposals. A small model echoes words it was never asked about, so only a term that
 * is one of the batch's own candidates survives; anything unreadable is simply no proposals.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PreScanReplies {

    /** A candidate the model chose to propose, with the type and gender it guessed. */
    record Proposal(NameCandidate candidate, TermType type, Gender gender) {}

    /** The key two spellings of one term share, so a merge compares them as the glossary does. */
    static String key(final String term) {
        return GlossaryKeys.of(term);
    }

    /**
     * Reads a reply against the batch it answers.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param replyText the model's reply
     * @param batch the batch's candidates by {@link #key}
     * @return the proposals that name a candidate of the batch; empty when the reply holds none or is unreadable
     */
    static List<Proposal> read(
            final ObjectMapper mapper, final String replyText, final Map<String, NameCandidate> batch) {
        final JsonNode terms = JsonReplies.tolerant(mapper, replyText)
                .map(root -> root.path("terms"))
                .orElse(null);
        if (terms == null || !terms.isArray()) {
            log.debug("Pre-scan reply holds no terms array; read as no proposals");
            return List.of();
        }
        final List<Proposal> kept = new ArrayList<>();
        int dropped = 0;
        for (final JsonNode node : terms) {
            final NameCandidate candidate = node.path("term").isTextual()
                    ? batch.get(key(node.path("term").asText()))
                    : null;
            if (candidate == null) {
                dropped++;
                continue;
            }
            kept.add(new Proposal(
                    candidate,
                    typeOf(node.path("type").asText("")),
                    genderOf(node.path("gender").asText(""))));
        }
        log.debug(
                "Pre-scan reply read: {} proposals kept, {} dropped as not a candidate of the batch",
                kept.size(),
                dropped);
        return kept;
    }

    private static TermType typeOf(final String wire) {
        return switch (key(wire)) {
            case "person" -> TermType.CHARACTER;
            case "place" -> TermType.PLACE;
            case "term" -> TermType.TERM;
            default -> TermType.OTHER;
        };
    }

    private static Gender genderOf(final String wire) {
        return switch (key(wire)) {
            case "female" -> Gender.FEMALE;
            case "male" -> Gender.MALE;
            case "neuter" -> Gender.NEUTER;
            default -> Gender.UNKNOWN;
        };
    }
}
