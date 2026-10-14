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
import org.jspecify.annotations.Nullable;
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
 * was given; without one it reads as "unsure" and the entry stays for the person to judge. A gender stands as verified only
 * when most of the distinct windows the model cites (strictly more than half, and at least two unless fewer were
 * shown) hold a pronoun of that gender the code gives the name, and the book-wide pronoun tally does not favour the
 * other gender; a favoured tally replaces the model's gender with its own, unverified. Otherwise the gender is kept as
 * a suggestion nobody has verified (15h.A2, 15i.N1).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermReviewReplies {

    private static final int MIN_MATCHING_WINDOWS = 2;
    private static final int FAVOURED_MIN = 3;
    private static final int FAVOURED_PERCENT = 60;
    private static final int PERCENT = 100;
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
     * @param read whether the type comes from the in-context readings' majority (15i.N5), which may correct a type a
     *     pronoun seed gave, rather than from one batched guess
     */
    record Verdict(
            GlossaryEntry entry,
            Kind kind,
            TermType type,
            Gender gender,
            String evidence,
            boolean genderVerified,
            boolean read) {

        /** A verdict of the batched review or of the code, whose type corrects no type already held. */
        Verdict(
                final GlossaryEntry entry,
                final Kind kind,
                final TermType type,
                final Gender gender,
                final String evidence,
                final boolean genderVerified) {
            this(entry, kind, type, gender, evidence, genderVerified, false);
        }

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
            return new Verdict(entry, kind, changed, gender, evidence, genderVerified, read);
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
        final List<Integer> cited = numbers(node.path("windows"));
        final GenderCall call = genderCall(genderOf(node.path("gender").asText("")), cited, evidence);
        log.debug(
                "Glossary review verdict on entry {}: {} gender={} windows={} verified={}",
                entry.id(),
                kind,
                call.gender(),
                cited,
                call.verified());
        return new Verdict(
                entry,
                kind,
                typeOf(kind, node.path("type").asText("")),
                call.gender(),
                kind == Kind.NOT_A_NAME ? quoted : "",
                call.verified());
    }

    /** The gender a verdict ends with and whether the book bears it out. */
    private record GenderCall(Gender gender, boolean verified) {}

    // A claimed gender stands verified only when most of the distinct windows it cites show it and the book-wide tally
    // does not favour the other gender; against a favoured tally the tally's gender replaces it, unverified.
    private static GenderCall genderCall(final Gender claimed, final List<Integer> cited, final Evidence evidence) {
        if (claimed == Gender.UNKNOWN) {
            return new GenderCall(claimed, false);
        }
        final Gender favoured = favouredBy(evidence.pronouns());
        if (favoured != null && claimed != Gender.NEUTER && favoured != claimed) {
            log.debug("Glossary review gender {} overruled by the book's pronoun tally: {}", claimed, favoured);
            return new GenderCall(favoured, false);
        }
        return new GenderCall(claimed, matches(claimed, cited, evidence));
    }

    // Strictly more than half of the distinct valid cited windows, and at least min(2, windows shown) of them.
    private static boolean matches(final Gender gender, final List<Integer> cited, final Evidence evidence) {
        final List<Integer> distinct = cited.stream()
                .distinct()
                .filter(number -> number >= 1 && number <= evidence.windows().size())
                .toList();
        final long matching = distinct.stream()
                .filter(number -> evidence.windows().get(number - 1).pronoun() == gender)
                .count();
        final boolean verified = matching * 2 > distinct.size()
                && matching >= Math.min(MIN_MATCHING_WINDOWS, evidence.windows().size());
        log.debug(
                "Glossary review gender {}: {} of {} distinct cited windows match, {} shown, verified={}",
                gender,
                matching,
                distinct.size(),
                evidence.windows().size(),
                verified);
        return verified;
    }

    /**
     * The gender the book-wide pronoun tally favours: three or more of the counted pronouns making 60 % or more of
     * them (15i.N1), which a model's opposite gender may only suggest against.
     *
     * @param tally the code's pronoun tally of the name or its cluster
     * @return the favoured gender, or null when neither side is favoured
     */
    static @Nullable Gender favouredBy(final PronounGender.Evidence tally) {
        final int female = tally.female();
        final int male = tally.male();
        final int total = female + male;
        log.debug("Glossary review pronoun tally: female={} male={}", female, male);
        if (female >= FAVOURED_MIN && female * PERCENT >= total * FAVOURED_PERCENT) {
            return Gender.FEMALE;
        }
        return male >= FAVOURED_MIN && male * PERCENT >= total * FAVOURED_PERCENT ? Gender.MALE : null;
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
