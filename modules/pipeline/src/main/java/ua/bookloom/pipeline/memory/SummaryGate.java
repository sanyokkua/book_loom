package ua.bookloom.pipeline.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.checks.LanguageIdentityCheck;
import ua.bookloom.pipeline.checks.Sentences;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.glossary.NameForms;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * The code check of one chapter-summary reply (design D10). A summary rides into every later call, so it is held to
 * at most three whole sentences; a sentence with a letter the target alphabet lacks or a source-only function word is
 * marked for one re-ask; a sentence holding angle-bracket markup (a leaked prompt tag) is dropped; a sentence naming a glossary entry the chapter never names is dropped, since the model took
 * it from elsewhere. Only the glossary is read, never written: no rendering is learned from a summary.
 */
@Slf4j
final class SummaryGate {

    private static final Pattern TERMINAL = Pattern.compile("[.!?…][»”\"’)\\]]*$");
    private static final Pattern MARKUP = Pattern.compile("</?\\p{L}|<\\p{L}");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}'’ʼ-]+");

    /** Where a reply came from, for the decision lines. */
    record Place(int chapter, int part) {}

    /**
     * What one reply kept.
     *
     * @param clean the sentences that passed every check, in reply order
     * @param faultyWords the words that failed the language check, in order and distinct; empty when none did
     * @param faulty how many sentences failed the language check, left out of {@code clean}
     * @param dropped how many sentences were cut or dropped by the other checks
     */
    record Reading(List<String> clean, List<String> faultyWords, int faulty, int dropped) {

        Reading {
            clean = List.copyOf(clean);
            faultyWords = List.copyOf(faultyWords);
        }
    }

    /** An entry the chapter never names, with every spelling a summary could name it by. */
    private record Absent(String term, List<String> forms) {}

    private final ObjectMapper mapper;
    private final @Nullable String sourceLanguage;
    private final String targetLanguage;
    private final String forbidden;
    private final List<Absent> absent;

    private SummaryGate(
            final ObjectMapper mapper,
            final @Nullable String sourceLanguage,
            final String targetLanguage,
            final List<Absent> absent) {
        this.mapper = mapper;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.forbidden = LanguageRules.bundled().forbiddenLetters(targetLanguage);
        this.absent = List.copyOf(absent);
    }

    /**
     * The gate of one chapter.
     *
     * @param mapper the non-null JSON reader
     * @param sourceLanguage the language the code reads the book in, or null when unknown
     * @param targetLanguage the non-null target language tag
     * @param map the non-null book map, whose glossary entries are checked
     * @param chapterText the non-null whole chapter's source, tokens removed
     * @return the gate; never null
     */
    static SummaryGate of(
            final ObjectMapper mapper,
            final @Nullable String sourceLanguage,
            final String targetLanguage,
            final BookMap map,
            final String chapterText) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(chapterText, "chapterText");
        final List<Absent> absent = map.entries().stream()
                .filter(entry -> TermMatch.namedCount(entry.names(), chapterText) == 0)
                .map(entry -> new Absent(entry.term(), formsOf(entry, targetLanguage)))
                .toList();
        log.debug("Chapter summary gate entries={} absent={}", map.entries().size(), absent.size());
        return new SummaryGate(
                Objects.requireNonNull(mapper, "mapper"),
                sourceLanguage,
                Objects.requireNonNull(targetLanguage, "targetLanguage"),
                absent);
    }

    /**
     * Checks one reply.
     *
     * @param content the non-null reply text
     * @param place the non-null chapter and part it answers
     * @param reasked {@code true} if this is the answer to the one re-ask, so a language fault drops its sentence
     * @return what it kept, or empty if the reply holds no summary text
     */
    Optional<Reading> read(final String content, final Place place, final boolean reasked) {
        final Optional<String> text = JsonReplies.tolerant(mapper, content)
                .map(root -> root.path("summary"))
                .filter(JsonNode::isTextual)
                .map(node -> node.asText().strip())
                .filter(summary -> !summary.isEmpty());
        if (text.isEmpty()) {
            log.debug("Chapter summary gate chapter={} part={} unreadable", place.chapter(), place.part());
            return Optional.empty();
        }
        return Optional.of(check(Sentences.split(text.get()), place, reasked));
    }

    /** What became of one sentence of a reply, named as its decision line names it. */
    private enum Decision {
        /** It passed every check. */
        KEPT("kept"),
        /** It came after the third sentence. */
        CUT("cut"),
        /** It was the reply's last piece and ended with no terminal mark. */
        FRAGMENT("fragment"),
        /** It failed the language check in a first answer, so the reply is asked once more. */
        REASK("reask"),
        /** It failed the language check in the answer to the re-ask. */
        DROPPED_LETTERS("dropped-letters"),
        /** It names an entry the chapter never names. */
        DROPPED_ENTRY("dropped-entry"),
        /** It holds prompt markup such as {@code <Names>} that leaked into the reply. */
        DROPPED_MARKUP("dropped-markup");

        private final String label;

        Decision(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** One sentence's decision with the entry it named, or null. */
    private record Verdict(Decision decision, @Nullable String entry) {}

    private Reading check(final List<String> pieces, final Place place, final boolean reasked) {
        final List<String> clean = new ArrayList<>();
        final Set<String> faultyWords = new LinkedHashSet<>();
        int faulty = 0;
        int dropped = 0;
        for (int index = 0; index < pieces.size(); index++) {
            final String sentence = pieces.get(index);
            final Verdict verdict = decide(sentence, index, pieces.size(), reasked, faultyWords);
            switch (verdict.decision()) {
                case KEPT -> clean.add(sentence);
                case REASK, DROPPED_LETTERS -> faulty++;
                case CUT, FRAGMENT, DROPPED_ENTRY, DROPPED_MARKUP -> dropped++;
            }
            log.debug(
                    "Chapter summary gate chapter={} part={} sentence={} decision={} entry={}",
                    place.chapter(),
                    place.part(),
                    index + 1,
                    verdict.decision().label(),
                    verdict.entry());
        }
        return new Reading(clean, List.copyOf(faultyWords), faulty, dropped);
    }

    // The checks in order: the place in the reply, the names, then the language, whose words the re-ask names.
    private Verdict decide(
            final String sentence, final int index, final int pieces, final boolean reasked, final Set<String> words) {
        if (index == pieces - 1 && !TERMINAL.matcher(sentence).find()) {
            return new Verdict(Decision.FRAGMENT, null);
        }
        if (index >= ChapterSummaryKept.MAX_SENTENCES) {
            return new Verdict(Decision.CUT, null);
        }
        if (MARKUP.matcher(sentence).find()) {
            return new Verdict(Decision.DROPPED_MARKUP, null);
        }
        final Optional<Absent> named = absent.stream()
                .filter(entry -> entry.forms().stream().anyMatch(form -> TermMatch.isNamedIn(form, sentence)))
                .findFirst();
        if (named.isPresent()) {
            return new Verdict(Decision.DROPPED_ENTRY, named.get().term());
        }
        final List<String> faults = languageFaults(sentence);
        if (faults.isEmpty()) {
            return new Verdict(Decision.KEPT, null);
        }
        words.addAll(faults);
        return new Verdict(reasked ? Decision.DROPPED_LETTERS : Decision.REASK, null);
    }

    private List<String> languageFaults(final String sentence) {
        final List<String> faults = new ArrayList<>();
        final Matcher matcher = WORD.matcher(sentence);
        while (matcher.find()) {
            final String word = matcher.group();
            if (word.toLowerCase(Locale.ROOT).codePoints().anyMatch(letter -> forbidden.indexOf(letter) >= 0)) {
                faults.add(word);
            }
        }
        faults.addAll(LanguageIdentityCheck.residueWords(sentence, sourceLanguage, targetLanguage));
        return faults;
    }

    // An entry is named by its source spellings and, when it has a target, by every form the target declines to.
    private static List<String> formsOf(final GlossaryEntry entry, final String targetLanguage) {
        final String target = entry.target();
        final Stream<String> targetForms = target == null || target.isBlank()
                ? Stream.empty()
                : NameForms.forms(target.strip(), entry.gender(), targetLanguage).all().stream();
        return Stream.concat(entry.names().stream(), targetForms).distinct().toList();
    }
}
