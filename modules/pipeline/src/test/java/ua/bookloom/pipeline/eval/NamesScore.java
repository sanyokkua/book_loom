package ua.bookloom.pipeline.eval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/**
 * What Names &amp; style left of a book, scored against its gold (15i.E1): the gender of every character the book
 * mentions at least {@link #MIN_MENTIONS} times (a wrong gender that is not marked suggested is "wrong but verified",
 * the worst kind), the type of every gold character, whether the written forms of one person (a gold cluster id)
 * agree on one gender and one spelling of the name, the junk a scan left, the real names it lost and the recurring
 * terms it kept and dropped.
 *
 * <p>Rules the numbers follow. A mention is a whole-word, case-sensitive occurrence of the gold term in a paragraph. A
 * gold character with no glossary entry counts as wrong for gender and type. A gold name (a character or a {@code
 * keep} term) is lost when no entry holds it as its term or an alias, compared without case, and a name that is both
 * a character and a keep term counts once. Junk is kept when a glossary entry has exactly the junk term as its term
 * (same case, so the real "Kessel Optik" is not the junk "KESSEL OPTIK"). The gender, type and cluster of a gold
 * character are read from the entry that holds its name as its term or, failing that, as an alias, like the lost names. A cluster's spelling is the first word of an
 * entry's target, without case; a cluster with two or more members is consistent when its entries hold at most one
 * known gender. A gender is "verified" while the entry is not marked suggested ({@code !isGenderSuggested()}); 15i.N0
 * replaces that test with the gender origin.
 *
 * @param eligible the gold characters with {@link #MIN_MENTIONS} or more mentions
 * @param genderCorrect how many of them ended with the gold gender
 * @param wrongButVerified how many of them ended with a known gender that is not the gold one and is not marked
 *     suggested
 * @param typeCorrect how many gold characters ended with the gold type
 * @param typeScored how many gold characters were scored for type
 * @param clusters how many gold clusters have two or more members
 * @param clustersWithOneGender how many of those hold at most one known gender among their entries
 * @param spellings the distinct spellings of each cluster's name, by cluster id
 * @param junkKept how many gold junk terms are in the glossary
 * @param lostNames the gold characters and {@code keep} names no entry holds, each once as the gold writes it
 * @param recurringKept how many gold recurring terms to keep are in the lexicon
 * @param recurringDropped how many gold recurring terms to keep are not in the lexicon
 * @param recurringJunkKept how many gold recurring terms to drop are in the lexicon
 * @param calls the model calls the run sent
 * @param minutes the run's time in minutes
 */
record NamesScore(
        int eligible,
        int genderCorrect,
        int wrongButVerified,
        int typeCorrect,
        int typeScored,
        int clusters,
        int clustersWithOneGender,
        Map<String, Integer> spellings,
        int junkKept,
        List<String> lostNames,
        int recurringKept,
        int recurringDropped,
        int recurringJunkKept,
        int calls,
        double minutes) {

    /** A character is scored for gender when the book mentions it this many times. */
    static final int MIN_MENTIONS = 5;

    private static final double MILLIS_PER_MINUTE = 60_000.0;
    private static final int ROUNDING_DIGITS = 3;

    /** Copies the spellings and the lost names. */
    NamesScore {
        spellings = Map.copyOf(spellings);
        lostNames = List.copyOf(lostNames);
    }

    /** How many gold characters and {@code keep} names no entry holds. */
    int realNamesLost() {
        return lostNames.size();
    }

    /**
     * Scores a final glossary and lexicon against the gold.
     *
     * @param gold the gold of the book
     * @param paragraphs the book's paragraphs, which give the mention counts
     * @param glossary the glossary after the run
     * @param lexicon the lexicon after the run
     * @param calls the model calls the run sent
     * @param elapsed the run's time
     * @return the score
     */
    static NamesScore of(
            final JudgementGold gold,
            final List<String> paragraphs,
            final List<GlossaryEntry> glossary,
            final List<LexiconEntry> lexicon,
            final int calls,
            final Duration elapsed) {
        Objects.requireNonNull(gold, "gold");
        Objects.requireNonNull(paragraphs, "paragraphs");
        final Map<String, GlossaryEntry> byTerm = indexByName(glossary);
        final int[] characters = scoreCharacters(gold, String.join("\n", paragraphs), byTerm);
        final Map<String, List<JudgementGold.Person>> clusters = clustersOf(gold);
        final int[] recurring = scoreRecurring(gold, lexicon);
        return new NamesScore(
                characters[0],
                characters[1],
                characters[2],
                characters[3],
                gold.characters().size(),
                (int) clusters.values().stream().filter(c -> c.size() >= 2).count(),
                (int) clusters.values().stream()
                        .filter(c -> c.size() >= 2 && isOneGender(entriesOf(c, byTerm)))
                        .count(),
                spellingsOf(clusters, byTerm),
                (int) gold.junk().stream()
                        .filter(junk -> glossary.stream().anyMatch(e -> e.term().equals(junk)))
                        .count(),
                lostNames(gold, glossary),
                recurring[0],
                recurring[1],
                recurring[2],
                calls,
                elapsed.toMillis() / MILLIS_PER_MINUTE);
    }

    /** Every entry under its term and, where no entry has that term, under each of its aliases. */
    private static Map<String, GlossaryEntry> indexByName(final List<GlossaryEntry> glossary) {
        final Map<String, GlossaryEntry> byName = new HashMap<>();
        glossary.forEach(entry -> byName.put(lower(entry.term()), entry));
        glossary.forEach(entry -> entry.names().forEach(name -> byName.putIfAbsent(lower(name), entry)));
        return byName;
    }

    /** The share of eligible characters with the gold gender; 1 when none is eligible. */
    double genderAccuracy() {
        return rate(genderCorrect, eligible);
    }

    /** The share of gold characters with the gold type; 1 when there is none. */
    double typeAccuracy() {
        return rate(typeCorrect, typeScored);
    }

    /** The share of multi-member clusters that hold at most one gender; 1 when there is none. */
    double clusterRate() {
        return rate(clustersWithOneGender, clusters);
    }

    /** The most spellings any cluster holds; 0 when there is no cluster. */
    int maxSpellings() {
        return spellings.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Whether nothing is wrong: every gender, type and cluster right, no junk, no lost name, no recurring error. */
    boolean isClean() {
        return genderCorrect == eligible
                && wrongButVerified == 0
                && typeCorrect == typeScored
                && clustersWithOneGender == clusters
                && maxSpellings() <= 1
                && junkKept == 0
                && lostNames.isEmpty()
                && recurringDropped == 0
                && recurringJunkKept == 0;
    }

    /** The score as one JSON object, numbers rounded to three decimals. */
    String json() {
        final String spelled = spellings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        return "{\"eligible\":" + eligible
                + ",\"genderCorrect\":" + genderCorrect
                + ",\"wrongButVerified\":" + wrongButVerified
                + ",\"genderAccuracy\":" + round(genderAccuracy())
                + ",\"typeCorrect\":" + typeCorrect
                + ",\"typeScored\":" + typeScored
                + ",\"typeAccuracy\":" + round(typeAccuracy())
                + ",\"clusters\":" + clusters
                + ",\"clustersWithOneGender\":" + clustersWithOneGender
                + ",\"clusterRate\":" + round(clusterRate())
                + ",\"spellings\":" + spelled
                + ",\"junkKept\":" + junkKept
                + ",\"realNamesLost\":" + realNamesLost()
                + ",\"recurringKept\":" + recurringKept
                + ",\"recurringDropped\":" + recurringDropped
                + ",\"recurringJunkKept\":" + recurringJunkKept
                + ",\"calls\":" + calls
                + ",\"minutes\":" + round(minutes) + "}";
    }

    /** Eligible, gender correct, wrong but verified and type correct, in that order. */
    private static int[] scoreCharacters(
            final JudgementGold gold, final String text, final Map<String, GlossaryEntry> byTerm) {
        int eligible = 0;
        int correct = 0;
        int verified = 0;
        int typeOk = 0;
        for (final JudgementGold.Person person : gold.characters()) {
            final GlossaryEntry entry = byTerm.get(lower(person.term()));
            typeOk += entry != null && entry.type() == person.type() ? 1 : 0;
            if (mentions(text, person.term()) < MIN_MENTIONS) {
                continue;
            }
            eligible++;
            correct += entry != null && entry.gender() == person.gender() ? 1 : 0;
            verified += isWrongButVerified(entry, person) ? 1 : 0;
        }
        return new int[] {eligible, correct, verified, typeOk};
    }

    private static boolean isWrongButVerified(final GlossaryEntry entry, final JudgementGold.Person person) {
        return entry != null
                && entry.gender() != person.gender()
                && entry.gender() != Gender.UNKNOWN
                && !entry.isGenderSuggested();
    }

    private static int mentions(final String text, final String term) {
        final var matcher = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])")
                .matcher(text);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    private static Map<String, List<JudgementGold.Person>> clustersOf(final JudgementGold gold) {
        return gold.characters().stream()
                .filter(person -> person.cluster() != null)
                .collect(Collectors.groupingBy(JudgementGold.Person::cluster, TreeMap::new, Collectors.toList()));
    }

    private static List<GlossaryEntry> entriesOf(
            final List<JudgementGold.Person> members, final Map<String, GlossaryEntry> byTerm) {
        return members.stream()
                .map(person -> byTerm.get(lower(person.term())))
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isOneGender(final List<GlossaryEntry> entries) {
        return entries.stream()
                        .map(GlossaryEntry::gender)
                        .filter(gender -> gender != Gender.UNKNOWN)
                        .distinct()
                        .count()
                <= 1;
    }

    private static Map<String, Integer> spellingsOf(
            final Map<String, List<JudgementGold.Person>> clusters, final Map<String, GlossaryEntry> byTerm) {
        final Map<String, Integer> spelled = new TreeMap<>();
        clusters.forEach((id, members) -> {
            final long distinct = entriesOf(members, byTerm).stream()
                    .map(GlossaryEntry::target)
                    .filter(target -> target != null && !target.isBlank())
                    .map(target -> lower(target.strip().split("\\s+", 2)[0]))
                    .distinct()
                    .count();
            if (distinct > 0) {
                spelled.put(id, (int) distinct);
            }
        });
        return spelled;
    }

    private static List<String> lostNames(final JudgementGold gold, final List<GlossaryEntry> glossary) {
        final Set<String> held = new HashSet<>();
        glossary.forEach(entry -> entry.names().forEach(name -> held.add(lower(name))));
        final Set<String> seen = new HashSet<>();
        return Stream.concat(gold.characters().stream().map(JudgementGold.Person::term), gold.keep().stream())
                .filter(name -> !held.contains(lower(name)) && seen.add(lower(name)))
                .toList();
    }

    /** Recurring terms kept, dropped and junk kept, in that order. */
    private static int[] scoreRecurring(final JudgementGold gold, final List<LexiconEntry> lexicon) {
        final Set<String> held =
                lexicon.stream().map(entry -> lower(entry.term())).collect(Collectors.toSet());
        int kept = 0;
        int dropped = 0;
        int junk = 0;
        for (final JudgementGold.Recurring term : gold.recurringTerms()) {
            final boolean isHeld = held.contains(lower(term.term()));
            kept += term.keep() && isHeld ? 1 : 0;
            dropped += term.keep() && !isHeld ? 1 : 0;
            junk += !term.keep() && isHeld ? 1 : 0;
        }
        return new int[] {kept, dropped, junk};
    }

    private static String lower(final String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static double rate(final int part, final int whole) {
        return whole == 0 ? 1.0 : (double) part / whole;
    }

    private static String round(final double value) {
        return BigDecimal.valueOf(value)
                .setScale(ROUNDING_DIGITS, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
