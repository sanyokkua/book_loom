package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/**
 * The cases behind a {@link NamesScore}, so a miss can be read and classified (model, code rule, triage or gold): each
 * eligible character whose gender is wrong with what it got and where that came from, the wrong types, the lost names,
 * each cluster's spellings, the junk kept, the recurring terms per gold decision, the lexicon terms the gold does not
 * list, and the whole final glossary. It scores nothing; the counts stay in {@link NamesScore}.
 *
 * @param wrongGender the eligible characters with a gender that is not the gold one
 * @param wrongType the gold characters whose entry has another type or no entry
 * @param lost the gold names no entry holds
 * @param spellings per cluster id, each member's {@code term → target}
 * @param junkKept the gold junk terms still in the glossary
 * @param recurringKept the gold terms to keep that the lexicon holds
 * @param recurringDropped the gold terms to keep that the lexicon lacks
 * @param recurringJunkKept the gold terms to drop that the lexicon holds
 * @param lexiconOther the lexicon terms the gold does not list, each with its rendering
 * @param glossary every final entry as {@code term | type | gender/origin | target | locked | aliases}
 */
record NamesDiagnostics(
        List<Miss> wrongGender,
        List<String> wrongType,
        List<String> lost,
        Map<String, List<String>> spellings,
        List<String> junkKept,
        List<String> recurringKept,
        List<String> recurringDropped,
        List<String> recurringJunkKept,
        List<String> lexiconOther,
        List<String> glossary) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One wrong gender.
     *
     * @param term the gold term
     * @param gold the gold gender
     * @param got the gender the entry ended with, UNKNOWN when there is no entry
     * @param origin the entry's gender origin, or null when it has none or there is no entry
     * @param entry the term of the entry that holds the name, or null when none does
     * @param mentions the gold term's case-sensitive mentions
     */
    record Miss(
            String term,
            Gender gold,
            Gender got,
            @Nullable GenderOrigin origin,
            @Nullable String entry,
            int mentions) {

        boolean isVerified() {
            return got != Gender.UNKNOWN && origin == GenderOrigin.VERIFIED;
        }
    }

    /** Copies every list and map. */
    NamesDiagnostics {
        wrongGender = List.copyOf(wrongGender);
        wrongType = List.copyOf(wrongType);
        lost = List.copyOf(lost);
        spellings = Map.copyOf(spellings);
        junkKept = List.copyOf(junkKept);
        recurringKept = List.copyOf(recurringKept);
        recurringDropped = List.copyOf(recurringDropped);
        recurringJunkKept = List.copyOf(recurringJunkKept);
        lexiconOther = List.copyOf(lexiconOther);
        glossary = List.copyOf(glossary);
    }

    /**
     * The cases of a final glossary and lexicon against the gold, read by the rules of {@link NamesScore}.
     *
     * @param gold the gold of the book
     * @param paragraphs the book's paragraphs, which give the mention counts
     * @param glossary the glossary after the run
     * @param lexicon the lexicon after the run
     * @param score the score of the same run, whose lost names are reused
     * @return the cases
     */
    static NamesDiagnostics of(
            final JudgementGold gold,
            final List<String> paragraphs,
            final List<GlossaryEntry> glossary,
            final List<LexiconEntry> lexicon,
            final NamesScore score) {
        Objects.requireNonNull(gold, "gold");
        final Map<String, GlossaryEntry> byName = byName(glossary);
        final String text = String.join("\n", paragraphs);
        final Set<String> listed =
                gold.recurringTerms().stream().map(term -> lower(term.term())).collect(Collectors.toSet());
        final Set<String> held = lexicon.stream().map(e -> lower(e.term())).collect(Collectors.toSet());
        return new NamesDiagnostics(
                wrongGender(gold, text, byName),
                wrongType(gold, byName),
                score.lostNames(),
                spellings(gold, byName),
                gold.junk().stream()
                        .filter(junk -> glossary.stream().anyMatch(e -> e.term().equals(junk)))
                        .toList(),
                recurring(gold, held, true, true),
                recurring(gold, held, true, false),
                recurring(gold, held, false, true),
                lexicon.stream()
                        .filter(e -> !listed.contains(lower(e.term())))
                        .map(e -> e.term() + " → " + e.chosen())
                        .toList(),
                glossary.stream().map(NamesDiagnostics::line).toList());
    }

    /** The cases as one JSON object. */
    ObjectNode json() {
        final ObjectNode root = MAPPER.createObjectNode();
        final ArrayNode misses = root.putArray("wrongGender");
        wrongGender.forEach(miss -> misses.addObject()
                .put("term", miss.term())
                .put("gold", miss.gold().name())
                .put("got", miss.got().name())
                .put("origin", miss.origin() == null ? null : miss.origin().name())
                .put("entry", miss.entry())
                .put("mentions", miss.mentions())
                .put("verified", miss.isVerified()));
        strings(
                root,
                "wrongVerified",
                wrongGender.stream().filter(Miss::isVerified).map(Miss::term).toList());
        strings(root, "wrongType", wrongType);
        strings(root, "lost", lost);
        final ObjectNode spelled = root.putObject("spellings");
        new TreeMap<>(spellings).forEach((id, lines) -> strings(spelled, id, lines));
        strings(root, "junkKept", junkKept);
        strings(root, "recurringKept", recurringKept);
        strings(root, "recurringDropped", recurringDropped);
        strings(root, "recurringJunkKept", recurringJunkKept);
        strings(root, "lexiconOther", lexiconOther);
        strings(root, "glossary", glossary);
        return root;
    }

    private static void strings(final ObjectNode node, final String field, final List<String> values) {
        final ArrayNode array = node.putArray(field);
        values.forEach(array::add);
    }

    private static Map<String, GlossaryEntry> byName(final List<GlossaryEntry> glossary) {
        final Map<String, GlossaryEntry> byName = new HashMap<>();
        glossary.forEach(entry -> byName.put(lower(entry.term()), entry));
        glossary.forEach(entry -> entry.names().forEach(name -> byName.putIfAbsent(lower(name), entry)));
        return byName;
    }

    private static List<Miss> wrongGender(
            final JudgementGold gold, final String text, final Map<String, GlossaryEntry> byName) {
        final List<Miss> misses = new ArrayList<>();
        for (final JudgementGold.Person person : gold.characters()) {
            final int mentions = NamesScore.mentions(text, person.term());
            final GlossaryEntry entry = byName.get(lower(person.term()));
            final Gender got = entry == null ? Gender.UNKNOWN : entry.gender();
            if (NamesScore.isGendered(person, text) && (entry == null || got != person.gender())) {
                misses.add(new Miss(
                        person.term(),
                        person.gender(),
                        got,
                        entry == null ? null : entry.genderOrigin(),
                        entry == null ? null : entry.term(),
                        mentions));
            }
        }
        return misses;
    }

    private static List<String> wrongType(final JudgementGold gold, final Map<String, GlossaryEntry> byName) {
        return gold.characters().stream()
                .filter(person -> {
                    final GlossaryEntry entry = byName.get(lower(person.term()));
                    return entry == null || entry.type() != person.type();
                })
                .map(person -> {
                    final GlossaryEntry entry = byName.get(lower(person.term()));
                    return person.term() + ": " + person.type() + " → " + (entry == null ? "none" : entry.type());
                })
                .toList();
    }

    private static Map<String, List<String>> spellings(
            final JudgementGold gold, final Map<String, GlossaryEntry> byName) {
        final Map<String, List<String>> spelled = new TreeMap<>();
        gold.characters().stream().filter(person -> person.cluster() != null).forEach(person -> {
            final GlossaryEntry entry = byName.get(lower(person.term()));
            spelled.computeIfAbsent(Objects.requireNonNull(person.cluster()), id -> new ArrayList<>())
                    .add(person.term() + " → " + (entry == null ? "(none)" : entry.term() + " → " + entry.target()));
        });
        return spelled;
    }

    private static List<String> recurring(
            final JudgementGold gold, final Set<String> held, final boolean keep, final boolean isHeld) {
        final Set<String> seen = new HashSet<>();
        return gold.recurringTerms().stream()
                .filter(term -> term.keep() == keep && held.contains(lower(term.term())) == isHeld)
                .map(JudgementGold.Recurring::term)
                .filter(term -> seen.add(lower(term)))
                .toList();
    }

    private static String line(final GlossaryEntry entry) {
        return String.join(
                " | ",
                entry.term(),
                entry.type().name(),
                entry.gender() + "/" + entry.genderOrigin(),
                String.valueOf(entry.target()) + "/" + entry.origin(),
                entry.locked() ? "locked" : "open",
                String.join(",", entry.aliases()));
    }

    private static String lower(final String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
