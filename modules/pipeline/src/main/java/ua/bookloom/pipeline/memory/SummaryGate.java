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
import java.util.stream.Collectors;
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
 * it from elsewhere, and so is one that spells a name by its source spelling when the entry has a target. A sentence
 * with more than {@link #MAX_UNKNOWN_WORDS} word the chapter's decided text, the names and the language's closed word
 * list do not hold is marked for the same one re-ask, which names those words: a made-up word reads like a real one
 * to the model and to the next call. Only the glossary is read, never written: no rendering is learned from a
 * summary.
 */
@Slf4j
final class SummaryGate {

    private static final Pattern TERMINAL = Pattern.compile("[.!?…][»”\"’)\\]]*$");
    private static final Pattern MARKUP = Pattern.compile("</?\\p{L}|<\\p{L}");
    /** The most unknown words a sentence may hold: one slip of inflection or spelling is let through. */
    static final int MAX_UNKNOWN_WORDS = 1;

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}'’ʼ-]+");

    /** Where a reply came from, for the decision lines. */
    record Place(int chapter, int part) {}

    /**
     * What one reply kept.
     *
     * @param clean the sentences that passed every check, in reply order
     * @param faultyWords the words that failed the language check, in order and distinct; empty when none did
     * @param unknownWords the words of the sentences the vocabulary check marked, in order and distinct; empty when
     *     none were
     * @param faulty how many sentences failed the language or the vocabulary check, left out of {@code clean}
     * @param dropped how many sentences were cut or dropped by the other checks
     */
    record Reading(List<String> clean, List<String> faultyWords, List<String> unknownWords, int faulty, int dropped) {

        Reading {
            clean = List.copyOf(clean);
            faultyWords = List.copyOf(faultyWords);
            unknownWords = List.copyOf(unknownWords);
        }

        /** Whether the reply is asked again: some sentence failed a check a second answer can mend. */
        boolean needsReask() {
            return !faultyWords.isEmpty() || !unknownWords.isEmpty();
        }
    }

    /** The words the language and the vocabulary checks collect over one reply. */
    private record Collected(Set<String> letters, Set<String> unknown) {

        Collected() {
            this(new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    /** An entry the chapter never names, with every spelling a summary could name it by. */
    private record Absent(String term, List<String> forms) {}

    private final ObjectMapper mapper;
    private final @Nullable String sourceLanguage;
    private final String targetLanguage;
    private final String forbidden;
    private final List<Absent> absent;
    private final List<Absent> sourceSpelled;
    private final SummaryVocabulary vocabulary;

    private SummaryGate(
            final ObjectMapper mapper,
            final @Nullable String sourceLanguage,
            final String targetLanguage,
            final List<Absent> absent,
            final List<Absent> sourceSpelled,
            final SummaryVocabulary vocabulary) {
        this.mapper = mapper;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.forbidden = LanguageRules.bundled().forbiddenLetters(targetLanguage);
        this.absent = List.copyOf(absent);
        this.sourceSpelled = List.copyOf(sourceSpelled);
        this.vocabulary = vocabulary;
    }

    /**
     * The gate of one chapter.
     *
     * @param mapper the non-null JSON reader
     * @param sourceLanguage the language the code reads the book in, or null when unknown
     * @param targetLanguage the non-null target language tag
     * @param map the non-null book map, whose glossary entries are checked
     * @param chapterText the non-null whole chapter's source, tokens removed
     * @param decidedLines the non-null decided target text of the chapter's segments; with none, the vocabulary check
     *     is off
     * @return the gate; never null
     */
    static SummaryGate of(
            final ObjectMapper mapper,
            final @Nullable String sourceLanguage,
            final String targetLanguage,
            final BookMap map,
            final String chapterText,
            final List<String> decidedLines) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(chapterText, "chapterText");
        final List<Absent> absent = map.entries().stream()
                .filter(entry -> TermMatch.namedCount(entry.names(), chapterText) == 0)
                .map(entry -> new Absent(entry.term(), formsOf(entry, targetLanguage)))
                .toList();
        final List<Absent> sourceSpelled = map.entries().stream()
                .map(entry -> new Absent(entry.term(), sourceSpellings(entry, targetLanguage)))
                .filter(spelled -> !spelled.forms().isEmpty())
                .toList();
        final SummaryVocabulary vocabulary = SummaryVocabulary.of(decidedLines, map.entries(), targetLanguage);
        log.debug(
                "Chapter summary gate entries={} absent={} sourceSpelled={} vocabularyUsable={}",
                map.entries().size(),
                absent.size(),
                sourceSpelled.size(),
                vocabulary.isUsable());
        return new SummaryGate(
                Objects.requireNonNull(mapper, "mapper"),
                sourceLanguage,
                Objects.requireNonNull(targetLanguage, "targetLanguage"),
                absent,
                sourceSpelled,
                vocabulary);
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
        DROPPED_MARKUP("dropped-markup"),
        /** It spells a name by its source spelling though the glossary gives the name a target. */
        DROPPED_SOURCE_NAME("dropped-source-name"),
        /** It holds words nothing in the book or the language's word list has, in a first answer. */
        REASK_UNKNOWN("reask-unknown"),
        /** It holds such words in the answer to the re-ask. */
        DROPPED_UNKNOWN("dropped-unknown");

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
        final Collected collected = new Collected();
        int faulty = 0;
        int dropped = 0;
        for (int index = 0; index < pieces.size(); index++) {
            final String sentence = pieces.get(index);
            final Verdict verdict = decide(sentence, index, pieces.size(), reasked, collected);
            switch (verdict.decision()) {
                case KEPT -> clean.add(sentence);
                case REASK, DROPPED_LETTERS, REASK_UNKNOWN, DROPPED_UNKNOWN -> faulty++;
                case CUT, FRAGMENT, DROPPED_ENTRY, DROPPED_MARKUP, DROPPED_SOURCE_NAME -> dropped++;
            }
            log.debug(
                    "Chapter summary gate chapter={} part={} sentence={} decision={} entry={}",
                    place.chapter(),
                    place.part(),
                    index + 1,
                    verdict.decision().label(),
                    verdict.entry());
        }
        return new Reading(clean, List.copyOf(collected.letters()), List.copyOf(collected.unknown()), faulty, dropped);
    }

    // The checks in order: the place in the reply, the names, then the language and the vocabulary, whose words the
    // re-ask names.
    private Verdict decide(
            final String sentence,
            final int index,
            final int pieces,
            final boolean reasked,
            final Collected collected) {
        final Optional<Verdict> structural = structural(sentence, index, pieces);
        if (structural.isPresent()) {
            return structural.get();
        }
        final List<String> faults = languageFaults(sentence);
        if (!faults.isEmpty()) {
            collected.letters().addAll(faults);
            return new Verdict(reasked ? Decision.DROPPED_LETTERS : Decision.REASK, null);
        }
        final List<String> unknown = vocabulary.isUsable() ? vocabulary.unknownIn(sentence) : List.of();
        if (unknown.size() > MAX_UNKNOWN_WORDS) {
            log.trace("Chapter summary gate unknown words {}", unknown);
            collected.unknown().addAll(unknown);
            return new Verdict(reasked ? Decision.DROPPED_UNKNOWN : Decision.REASK_UNKNOWN, null);
        }
        return new Verdict(Decision.KEPT, null);
    }

    private Optional<Verdict> structural(final String sentence, final int index, final int pieces) {
        if (index == pieces - 1 && !TERMINAL.matcher(sentence).find()) {
            return Optional.of(new Verdict(Decision.FRAGMENT, null));
        }
        if (index >= ChapterSummaryKept.MAX_SENTENCES) {
            return Optional.of(new Verdict(Decision.CUT, null));
        }
        if (MARKUP.matcher(sentence).find()) {
            return Optional.of(new Verdict(Decision.DROPPED_MARKUP, null));
        }
        return namedEntry(absent, sentence)
                .map(term -> new Verdict(Decision.DROPPED_ENTRY, term))
                .or(() -> namedEntry(sourceSpelled, sentence)
                        .map(term -> new Verdict(Decision.DROPPED_SOURCE_NAME, term)));
    }

    private static Optional<String> namedEntry(final List<Absent> entries, final String sentence) {
        return entries.stream()
                .filter(entry -> entry.forms().stream().anyMatch(form -> TermMatch.isNamedIn(form, sentence)))
                .map(Absent::term)
                .findFirst();
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

    // A source spelling the target spells differently; one both languages write alike is no slip.
    private static List<String> sourceSpellings(final GlossaryEntry entry, final String targetLanguage) {
        final String target = entry.target();
        if (target == null || target.isBlank()) {
            return List.of();
        }
        final Set<String> targetSpellings =
                NameForms.forms(target.strip(), entry.gender(), targetLanguage).all().stream()
                        .map(form -> form.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
        return entry.names().stream()
                .filter(name -> !targetSpellings.contains(name.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
