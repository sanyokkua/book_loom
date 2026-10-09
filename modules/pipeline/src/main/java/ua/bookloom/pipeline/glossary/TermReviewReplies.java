package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Reads one glossary-review reply. Only a verdict on a term of the batch survives, and a verdict word or type the
 * schema does not list reads as "no opinion", so an unreadable reply changes nothing. A "not-a-name" verdict removes
 * an entry, so it stands only with evidence: a phrase the model quotes that really occurs in one of the windows it
 * was given; without one it reads as "unsure" and the entry stays for the person to judge. A gender stands only when
 * one of the windows the model cites holds a pronoun of that gender the code gives the name; otherwise it is kept as
 * a suggestion nobody has verified (15h.A2).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermReviewReplies {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern QUOTE_MARKS = Pattern.compile("[\"'“”‘’«»„…]|\\.{3}");

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
     * @param evidence the phrase the model quoted for a "not-a-name" verdict, empty when it quoted none or the verdict
     *     is another
     * @param genderVerified whether a window the model cited shows the gender by a pronoun the code gives the name;
     *     false for {@link Gender#UNKNOWN}
     */
    record Verdict(
            GlossaryEntry entry, Kind kind, TermType type, Gender gender, String evidence, boolean genderVerified) {

        /**
         * The verdict the code reaches with no model, from a strong pronoun case: a name, a person, the gender shown.
         *
         * @param entry the entry decided
         * @param gender the gender the pronouns show
         * @return a verified verdict
         */
        static Verdict decided(final GlossaryEntry entry, final Gender gender) {
            return new Verdict(entry, Kind.NAME, TermType.CHARACTER, gender, "", true);
        }

        /**
         * This verdict with another type.
         *
         * @param changed the non-null type
         * @return a copy with the type set
         */
        Verdict withType(final TermType changed) {
            return new Verdict(entry, kind, changed, gender, evidence, genderVerified);
        }
    }

    /**
     * Reads a reply against the batch it answers.
     *
     * @param mapper the mapper the tolerant reader uses
     * @param replyText the model's reply
     * @param batch the batch's entries by their {@link GlossaryKeys} key
     * @param evidence the windows each term was asked with, by the term as listed
     * @return the verdicts on entries of the batch; empty when the reply holds none or is unreadable
     */
    static List<Verdict> read(
            final ObjectMapper mapper,
            final String replyText,
            final Map<String, GlossaryEntry> batch,
            final Map<String, Evidence> evidence) {
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
                kept.add(verdictOn(entry, node, evidence.getOrDefault(entry.term(), Evidence.NONE)));
            }
        }
        log.debug("Glossary review reply read: {} verdicts kept of {}", kept.size(), verdicts.size());
        return kept;
    }

    private static Verdict verdictOn(final GlossaryEntry entry, final JsonNode node, final Evidence evidence) {
        final String quoted = node.path("evidence").asText("").strip();
        Kind kind = kindOf(node.path("verdict").asText(""));
        if (kind == Kind.NOT_A_NAME && !quotes(quoted, evidence)) {
            log.debug("Glossary review verdict not-a-name on entry {} read as unsure: no quoted evidence", entry.id());
            log.trace("Glossary review evidence for {} was '{}'", entry.term(), quoted);
            kind = Kind.UNSURE;
        }
        final Gender gender = genderOf(node.path("gender").asText(""));
        final List<Integer> cited = numbers(node.path("windows"));
        final boolean verified = gender != Gender.UNKNOWN && evidence.shows(cited, gender);
        log.debug(
                "Glossary review verdict on entry {}: {} gender={} windows={} verified={}",
                entry.id(),
                kind,
                gender,
                cited,
                verified);
        return new Verdict(
                entry,
                kind,
                typeOf(kind, node.path("type").asText("")),
                gender,
                kind == Kind.NOT_A_NAME ? quoted : "",
                verified);
    }

    private static List<Integer> numbers(final JsonNode windows) {
        final List<Integer> numbers = new ArrayList<>();
        if (windows.isArray()) {
            // A number written as a string reads too; anything else reads as 0, which names no window.
            windows.forEach(number -> numbers.add(number.asInt(0)));
        }
        return numbers;
    }

    // The quote may drop the sentence's quotes, ellipses and case, but it must be words the sentence holds.
    private static boolean quotes(final String quoted, final Evidence evidence) {
        final String wanted = squeezed(quoted);
        return !wanted.isEmpty()
                && evidence.windows().stream()
                        .map(window -> squeezed(window.text()))
                        .anyMatch(text -> text.contains(wanted));
    }

    private static String squeezed(final String text) {
        return QUOTE_MARKS
                .matcher(WHITESPACE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" "))
                .replaceAll("")
                .strip();
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
