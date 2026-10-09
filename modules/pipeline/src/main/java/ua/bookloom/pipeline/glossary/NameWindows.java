package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.glossary.PronounReader.Mention;
import ua.bookloom.pipeline.glossary.PronounReader.Pronoun;

/**
 * The evidence the glossary review shows the model for each name (15h.A2): a few numbered windows from across the book
 * — the first use, then one from each stretch of the rest — each the sentence that holds the name with one sentence
 * before and after, a narration window whose pronoun the code can give the name preferred within its stretch, and the
 * code's pronoun tally over the whole book. Each window records the pronoun the code gives the name there, so a
 * gender the model claims can be checked against the windows it cites. A name judged from its first sentence or two
 * was the old way, and it gave a woman the gender of the speaker's "he" (Burning Chrome, 2026-10-09).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameWindows {

    /** How many windows a name shows. */
    static final int WINDOWS = 4;

    /** How many windows a name used more than {@link #MANY_USES} times shows. */
    static final int MANY_WINDOWS = 6;

    /** Above this many uses a name shows {@link #MANY_WINDOWS} windows. */
    static final int MANY_USES = 30;

    /** How many windows a place shows; its gender is never read from the book. */
    static final int PLACE_WINDOWS = 2;

    /** A sentence longer than this is cut, so one long paragraph cannot crowd out the batch. */
    static final int MAX_SENTENCE_CHARS = 200;

    private static final int MAX_TERM_WORDS = 3;
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * One window of book text around a use of the name.
     *
     * @param text the sentence before, the sentence that holds the name and the sentence after, joined by spaces
     * @param pronoun the gender of the pronoun the code gives the name in this window — outside speech, after the name
     *     with no other name between — or null when there is none
     */
    record Window(String text, @Nullable Gender pronoun) {}

    /**
     * How the book uses one name.
     *
     * @param count how many times it occurs, in any case
     * @param windows the windows shown, in book order; window {@code n} is number {@code n + 1} in the prompt
     * @param pronouns the pronouns the code read after the name across the whole book
     */
    record Evidence(int count, List<Window> windows, PronounGender.Evidence pronouns) {

        static final Evidence NONE = new Evidence(0, List.of(), new PronounGender.Evidence(List.of(), List.of()));

        Evidence {
            windows = List.copyOf(windows);
        }

        /**
         * Whether a cited window holds a pronoun of the gender, which is what a gender the model claims rests on.
         *
         * @param numbers the window numbers the model cited, from 1; numbers outside the shown windows are ignored
         * @param gender the gender the model claims
         * @return {@code true} if one cited window's pronoun is of that gender, {@code false} otherwise
         */
        boolean shows(final List<Integer> numbers, final Gender gender) {
            return numbers.stream()
                    .filter(number -> number >= 1 && number <= windows.size())
                    .anyMatch(number -> windows.get(number - 1).pronoun() == gender);
        }
    }

    /** One use of a name: the segment, where the name starts in its visible text, and the sentence that holds it. */
    private record Use(int segment, int start, int sentence) {}

    /**
     * Gathers the windows for the entries.
     *
     * @param segments the book's segments; never null
     * @param entries the entries asked about; never null
     * @param languageTag the source language's tag, which picks the pronouns; null reads English
     * @return the evidence by term as held; a term the book never uses maps to {@link Evidence#NONE}
     */
    static Map<String, Evidence> of(
            final List<Segment> segments, final List<GlossaryEntry> entries, @Nullable final String languageTag) {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(entries, "entries");
        final List<Occurrences.Read> reads = segments.stream()
                .map(segment -> Occurrences.read(segment.masked()))
                .toList();
        final Map<String, List<Use>> uses = usesOf(reads, entries);
        final List<String> texts = Tokens.visibleTexts(segments);
        final Map<String, Evidence> evidence = new LinkedHashMap<>();
        for (final GlossaryEntry entry : entries) {
            final List<Use> found = uses.getOrDefault(entry.term(), List.of());
            evidence.put(entry.term(), evidenceOf(entry, found, reads, texts, languageTag));
        }
        log.debug(
                "Name windows over {} segments for {} entries, {} used in the book",
                segments.size(),
                entries.size(),
                uses.size());
        return evidence;
    }

    private static Evidence evidenceOf(
            final GlossaryEntry entry,
            final List<Use> uses,
            final List<Occurrences.Read> reads,
            final List<String> texts,
            @Nullable final String languageTag) {
        final PronounGender.Evidence pronouns = PronounGender.of(entry.term(), texts, languageTag);
        if (uses.isEmpty()) {
            return new Evidence(0, List.of(), pronouns);
        }
        final WindowReader reader = new WindowReader(
                PronounReader.of(entry.term(), languageTag == null ? DEFAULT_LANGUAGE : languageTag),
                reads,
                new HashMap<>());
        final List<Use> chosen = chosen(uses, wanted(entry, uses.size()), reader);
        final List<Window> windows = chosen.stream().map(reader::window).toList();
        log.trace(
                "Name windows for {}: {} uses, windows at {}, pronouns {}",
                entry.term(),
                uses.size(),
                chosen,
                pronouns.compact());
        return new Evidence(uses.size(), windows, pronouns);
    }

    private static int wanted(final GlossaryEntry entry, final int count) {
        if (entry.type() == TermType.PLACE) {
            return PLACE_WINDOWS;
        }
        return count > MANY_USES ? MANY_WINDOWS : WINDOWS;
    }

    /**
     * The first use, then from each of the equal stretches the rest is cut into, the first use whose window gives the
     * name a pronoun, else the stretch's middle use; a sentence already shown is not shown twice.
     */
    private static List<Use> chosen(final List<Use> uses, final int wanted, final WindowReader reader) {
        final List<Use> chosen = new ArrayList<>();
        final Set<Long> sentences = new HashSet<>();
        chosen.add(uses.getFirst());
        sentences.add(sentenceKey(uses.getFirst()));
        final List<Use> rest = uses.subList(1, uses.size());
        final int stretches = wanted - 1;
        for (int stretch = 0; stretch < stretches && !rest.isEmpty(); stretch++) {
            final List<Use> part =
                    rest.subList(stretch * rest.size() / stretches, (stretch + 1) * rest.size() / stretches);
            final List<Use> unseen = part.stream()
                    .filter(use -> !sentences.contains(sentenceKey(use)))
                    .toList();
            if (unseen.isEmpty()) {
                continue;
            }
            final Use pick = unseen.stream()
                    .filter(use -> reader.pronounOf(use) != null)
                    .findFirst()
                    .orElse(unseen.get(unseen.size() / 2));
            chosen.add(pick);
            sentences.add(sentenceKey(pick));
        }
        chosen.sort((left, right) -> Long.compare(sentenceKey(left), sentenceKey(right)));
        return chosen;
    }

    private static long sentenceKey(final Use use) {
        return ((long) use.segment() << Integer.SIZE) | use.sentence();
    }

    private static Map<String, List<Use>> usesOf(
            final List<Occurrences.Read> reads, final List<GlossaryEntry> entries) {
        final Map<String, String> termByKey = new HashMap<>();
        entries.forEach(
                entry -> termByKey.put(keyOf(Occurrences.read(entry.term()).words()), entry.term()));
        final Map<String, List<Use>> uses = new HashMap<>();
        for (int segment = 0; segment < reads.size(); segment++) {
            final List<Occurrences.Word> words = reads.get(segment).words();
            for (int start = 0; start < words.size(); start++) {
                addUses(words, segment, start, termByKey, uses);
            }
        }
        return uses;
    }

    private static void addUses(
            final List<Occurrences.Word> words,
            final int segment,
            final int start,
            final Map<String, String> termByKey,
            final Map<String, List<Use>> uses) {
        for (int end = start + 1; end <= Math.min(words.size(), start + MAX_TERM_WORDS); end++) {
            final String term = termByKey.get(keyOf(words.subList(start, end)));
            if (term != null) {
                final Occurrences.Word first = words.get(start);
                uses.computeIfAbsent(term, _ -> new ArrayList<>())
                        .add(new Use(segment, first.start(), first.sentence()));
            }
        }
    }

    private static String keyOf(final List<Occurrences.Word> words) {
        return String.join(" ", words.stream().map(Occurrences.Word::key).toList());
    }

    /** Reads the windows of one name, keeping each segment's mentions once they are read. */
    private record WindowReader(
            @Nullable PronounReader pronouns, List<Occurrences.Read> reads, Map<Integer, List<Mention>> mentions) {

        Window window(final Use use) {
            final Occurrences.Read read = reads.get(use.segment());
            final List<String> sentences = read.sentences();
            final int from = Math.max(0, use.sentence() - 1);
            final int to = Math.min(sentences.size(), use.sentence() + 2);
            final String text = String.join(
                    " ",
                    sentences.subList(from, to).stream().map(NameWindows::cut).toList());
            return new Window(text, pronounOf(use));
        }

        // The pronoun the code gives the name at this use, when it lies within the window's last sentence.
        @Nullable
        Gender pronounOf(final Use use) {
            final Occurrences.Read read = reads.get(use.segment());
            final List<Mention> found = mentions.computeIfAbsent(
                    use.segment(), segment -> pronouns == null ? List.of() : pronouns.read(read.text()));
            final int windowEnd = sentenceStart(read, use.sentence() + 2);
            return found.stream()
                    .filter(mention -> mention.start() == use.start())
                    .map(Mention::attributed)
                    .filter(Objects::nonNull)
                    .filter(pronoun -> pronoun.end() <= windowEnd)
                    .map(Pronoun::gender)
                    .findFirst()
                    .orElse(null);
        }

        private static int sentenceStart(final Occurrences.Read read, final int sentence) {
            return read.words().stream()
                    .filter(word -> word.sentence() == sentence)
                    .mapToInt(Occurrences.Word::start)
                    .findFirst()
                    .orElse(read.text().length());
        }
    }

    private static String cut(final String sentence) {
        return sentence.length() > MAX_SENTENCE_CHARS ? sentence.substring(0, MAX_SENTENCE_CHARS) + "…" : sentence;
    }
}
