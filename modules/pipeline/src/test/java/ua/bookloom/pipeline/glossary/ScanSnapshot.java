package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * What the six readers of a book answer, read from the book's index, as a JSON-ready tree: the name scan (whole story
 * and its first half, as the decision follow-up scans a subset), the key-term scan, the frequent words, the name
 * windows and pronoun tallies of the most frequent names, the term evidence of the most frequent terms and the
 * unknown-gender mention counts. The tree is held to the committed golden file, which was recorded before the readers
 * were switched from the book's segments to its index.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScanSnapshot {

    static final int MIN_COUNT = 1;
    static final int PROBE_NAMES = 6;
    static final int PROBE_TERMS = 5;
    static final int FREQUENT_LIMIT = 20;
    private static final int PROBE_NAMES_AS_TERMS = 3;
    private static final String PROJECT = "golden";

    /** The index path: each reader reads the book's index. */
    static Map<String, Object> overIndex(final Document document) {
        return of(new IndexReaders(document));
    }

    private static Map<String, Object> of(final IndexReaders readers) {
        final List<NameCandidate> names = readers.names(false, MIN_COUNT);
        final List<NameCandidate> keyTerms = readers.keyTerms();
        final List<GlossaryEntry> probes = names.stream()
                .limit(PROBE_NAMES)
                .map(candidate -> new GlossaryEntry(
                        "g:" + candidate.term(),
                        PROJECT,
                        candidate.term(),
                        null,
                        TermType.CHARACTER,
                        Gender.UNKNOWN,
                        false))
                .toList();
        final Set<String> terms = new LinkedHashSet<>();
        keyTerms.stream().limit(PROBE_TERMS).map(NameCandidate::term).forEach(terms::add);
        probes.stream().limit(PROBE_NAMES_AS_TERMS).map(GlossaryEntry::term).forEach(terms::add);

        final Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("storySegments", readers.storySegments());
        snapshot.put("nameScan", candidates(names));
        snapshot.put("nameScanFirstHalf", candidates(readers.names(true, MIN_COUNT)));
        snapshot.put("keyTerms", candidates(keyTerms));
        snapshot.put("frequentWords", candidates(readers.frequentWords(FREQUENT_LIMIT)));
        snapshot.put("windows", windows(readers.windows(probes)));
        snapshot.put("termEvidence", evidence(readers.evidence(List.copyOf(terms))));
        snapshot.put("unknownGenderMentions", mentions(readers.mentions(probes)));
        snapshot.put("pronouns", pronouns(probes, readers));
        return snapshot;
    }

    private static List<Object> candidates(final List<NameCandidate> candidates) {
        final List<Object> out = new ArrayList<>();
        for (final NameCandidate candidate : candidates) {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("term", candidate.term());
            row.put("count", candidate.count());
            row.put("firstSentence", candidate.firstSentence());
            row.put("aliases", candidate.aliases());
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> windows(final Map<String, NameWindows.Evidence> evidence) {
        final Map<String, Object> out = new LinkedHashMap<>();
        evidence.forEach((term, found) -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("count", found.count());
            row.put(
                    "windows",
                    found.windows().stream()
                            .map(window -> {
                                final Map<String, Object> shown = new LinkedHashMap<>();
                                shown.put("text", window.text());
                                shown.put(
                                        "pronoun",
                                        window.pronoun() == null
                                                ? null
                                                : window.pronoun().name());
                                return shown;
                            })
                            .toList());
            row.put("pronouns", pronounLists(found.pronouns()));
            out.put(term, row);
        });
        return out;
    }

    private static Map<String, Object> evidence(final Map<String, TermEvidence.Evidence> evidence) {
        final Map<String, Object> out = new LinkedHashMap<>();
        evidence.forEach((term, found) -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("count", found.count());
            row.put("examples", found.examples());
            out.put(term, row);
        });
        return out;
    }

    private static List<Object> mentions(final List<UnknownGender> found) {
        final List<Object> out = new ArrayList<>();
        for (final UnknownGender gender : found) {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("term", gender.term());
            row.put("mentions", gender.mentions());
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> pronouns(final List<GlossaryEntry> probes, final IndexReaders readers) {
        final Map<String, Object> out = new LinkedHashMap<>();
        probes.forEach(probe -> out.put(probe.term(), pronounLists(readers.pronouns(probe.term()))));
        return out;
    }

    private static Map<String, Object> pronounLists(final PronounGender.Evidence evidence) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("female", evidence.femaleWords());
        row.put("male", evidence.maleWords());
        row.put("femaleObject", evidence.femaleObjectWords());
        row.put("maleObject", evidence.maleObjectWords());
        return row;
    }

    /** The book is read once into the index and every call asks it. */
    private static final class IndexReaders {

        private final String language;
        private final BookIndex index;
        private final List<Integer> story;

        IndexReaders(final Document document) {
            // Every golden book is English prose; the plain-text one declares nothing, which now means Auto (no pronoun
            // table), so the readers are told the language the person would have chosen.
            this.language = "en";
            this.index = BookIndex.of(document);
            this.story = index.story();
        }

        int storySegments() {
            return story.size();
        }

        List<NameCandidate> names(final boolean firstHalfOnly, final int minCount) {
            return FrequencyScan.candidates(
                    index, firstHalfOnly ? story.subList(0, story.size() / 2) : story, minCount, language);
        }

        List<NameCandidate> keyTerms() {
            return KeyTermScan.candidates(index, story, language);
        }

        List<NameCandidate> frequentWords(final int limit) {
            return KeyTermScan.frequentWords(index, story, language, limit);
        }

        Map<String, NameWindows.Evidence> windows(final List<GlossaryEntry> entries) {
            return NameWindows.of(index, story, entries, language);
        }

        Map<String, TermEvidence.Evidence> evidence(final Collection<String> terms) {
            return TermEvidence.of(index, story, terms);
        }

        List<UnknownGender> mentions(final List<GlossaryEntry> entries) {
            return CharacterMentions.unknown(entries, index, 1);
        }

        PronounGender.Evidence pronouns(final String term) {
            return PronounGender.of(term, index, story, language);
        }
    }
}
