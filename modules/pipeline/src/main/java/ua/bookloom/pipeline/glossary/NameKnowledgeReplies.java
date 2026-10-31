package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.PlaceKind;
import ua.bookloom.api.project.TermType;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Reads the name-knowledge replies and applies a kept answer to its entry (design D5). An item is kept only when its
 * {@code term} repeats a name the batch asked, because a model that answers "Tobias" for "Tobin" may be answering for
 * another name; an item with a gender outside the three offered is not kept either. A kept answer is a likely value:
 * its gender goes only to a character whose gender no higher origin holds, its kind only to an entry whose type is still
 * {@link TermType#OTHER} (unset), and the answer itself is always recorded, so a low confidence and a rule that
 * disagrees can be shown.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameKnowledgeReplies {

    // A model that leaves the confidence out is taken as unsure, so its answer is shown as low confidence.
    private static final double UNSTATED_CONFIDENCE = 0.5;

    /** The kinds a category names: the type and, for a place, its kind. */
    private record Kind(TermType type, @Nullable PlaceKind placeKind) {}

    /**
     * The kept answers of one reply.
     *
     * @param mapper the mapper the reply is read with; never null
     * @param reply the reply's content; never null
     * @param asked the entries the batch asked about; never null
     * @return the answer per entry id for every item that repeats an asked name; never null, empty when the reply
     *     cannot be read
     */
    static Map<String, NameKnowledge> read(
            final ObjectMapper mapper, final String reply, final List<GlossaryEntry> asked) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(asked, "asked");
        final Map<String, GlossaryEntry> byKey = new LinkedHashMap<>();
        asked.forEach(entry -> byKey.putIfAbsent(GlossaryKeys.of(entry.term()), entry));
        final Map<String, NameKnowledge> kept = new LinkedHashMap<>();
        int dropped = 0;
        for (final JsonNode item : items(mapper, reply)) {
            final GlossaryEntry entry =
                    byKey.get(GlossaryKeys.of(item.path("term").asText("")));
            final Optional<NameKnowledge> answer = entry == null ? Optional.empty() : answerOf(item);
            if (entry == null || answer.isEmpty()) {
                dropped++;
                log.trace("Name knowledge item dropped: {}", item);
                continue;
            }
            kept.putIfAbsent(entry.id(), answer.get());
        }
        log.debug("Name knowledge reply asked={} kept={} dropped={}", asked.size(), kept.size(), dropped);
        return kept;
    }

    /**
     * The entry with the answer recorded and the values it may take: the kind where the type is unset, and for a
     * character the gender where no person, book or rule value is held.
     *
     * @param entry the entry asked about; never null
     * @param known the answer for it; never null
     * @return the entry as the answer leaves it
     */
    static GlossaryEntry applyTo(final GlossaryEntry entry, final NameKnowledge known) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(known, "known");
        final boolean takesKind = entry.type() == TermType.OTHER && known.type() != TermType.OTHER;
        GlossaryEntry applied = takesKind
                ? rebuilt(entry, known.type(), known.placeKind(), Origin.LIKELY, known)
                : recorded(entry, known);
        final boolean takesGender = applied.type() == TermType.CHARACTER
                && known.gender() != Gender.UNKNOWN
                && Origin.LIKELY.mayReplace(entry.genderOrigin());
        if (takesGender) {
            applied = applied.withGenderFrom(known.gender(), Origin.LIKELY);
        }
        log.debug(
                "Name knowledge for entry {}: kind taken {}, gender taken {} (held origin {}), confidence {}",
                entry.id(),
                takesKind,
                takesGender,
                entry.genderOrigin(),
                known.confidence());
        return applied;
    }

    /**
     * The entry with the answer recorded and nothing else changed.
     *
     * @param entry the entry; never null
     * @param known the answer; never null
     * @return a copy holding the answer
     */
    static GlossaryEntry recorded(final GlossaryEntry entry, final NameKnowledge known) {
        return rebuilt(entry, entry.type(), entry.placeKind(), entry.typeOrigin(), known);
    }

    // The record has one canonical shape; a kind and an answer are set through it.
    private static GlossaryEntry rebuilt(
            final GlossaryEntry entry,
            final TermType type,
            @Nullable final PlaceKind placeKind,
            final Origin typeOrigin,
            final NameKnowledge known) {
        return new GlossaryEntry(
                entry.id(),
                entry.projectId(),
                entry.term(),
                entry.forms(),
                entry.lemmaInferred(),
                entry.target(),
                entry.targetState(),
                entry.targetOrigin(),
                type,
                placeKind,
                typeOrigin,
                entry.gender(),
                entry.genderOrigin(),
                entry.genderSeedTried(),
                entry.noGenderEvidence(),
                entry.meaning(),
                entry.meaningChoice(),
                known);
    }

    private static Iterable<JsonNode> items(final ObjectMapper mapper, final String reply) {
        final int from = reply.indexOf('{');
        final int to = reply.lastIndexOf('}');
        if (from < 0 || to <= from) {
            log.warn("Name knowledge reply holds no JSON object; every name in it counts as unanswered");
            return List.of();
        }
        try {
            final JsonNode items =
                    mapper.readTree(reply.substring(from, to + 1)).path("items");
            return items.isArray() ? items : List.of();
        } catch (IOException cut) {
            log.warn("Name knowledge reply could not be read; every name in it counts as unanswered");
            return List.of();
        }
    }

    private static Optional<NameKnowledge> answerOf(final JsonNode item) {
        final Gender gender =
                switch (item.path("gender").asText("").strip().toLowerCase(Locale.ROOT)) {
                    case "male" -> Gender.MALE;
                    case "female" -> Gender.FEMALE;
                    case "neuter" -> Gender.NEUTER;
                    default -> Gender.UNKNOWN;
                };
        if (gender == Gender.UNKNOWN) {
            return Optional.empty();
        }
        final Kind kind = kindOf(item.path("category").asText(""));
        final JsonNode confidence = item.path("confidence");
        final double sure = confidence.isNumber() ? confidence.asDouble() : UNSTATED_CONFIDENCE;
        return Optional.of(new NameKnowledge(gender, kind.type(), kind.placeKind(), Math.clamp(sure, 0.0, 1.0)));
    }

    private static Kind kindOf(final String category) {
        return switch (category.strip().toLowerCase(Locale.ROOT)) {
            case "person" -> new Kind(TermType.CHARACTER, null);
            case "city" -> new Kind(TermType.PLACE, PlaceKind.CITY);
            case "river" -> new Kind(TermType.PLACE, PlaceKind.RIVER);
            case "country" -> new Kind(TermType.PLACE, PlaceKind.COUNTRY);
            case "region" -> new Kind(TermType.PLACE, PlaceKind.REGION);
            case "place" -> new Kind(TermType.PLACE, null);
            case "organisation", "organization" -> new Kind(TermType.ORGANISATION, null);
            case "product" -> new Kind(TermType.PRODUCT, null);
            case "title" -> new Kind(TermType.TITLE, null);
            case "term" -> new Kind(TermType.TERM, null);
            default -> new Kind(TermType.OTHER, null);
        };
    }
}
