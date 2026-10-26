package ua.bookloom.pipeline.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;

/**
 * Reads a book once: every segment's masked text goes through {@link Occurrences#read}, so the postings agree with the
 * name scans word for word, and each word's counts are tallied as it is met, where the honorific and standalone
 * decisions are still at hand.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IndexBuilder {

    private final List<SegmentInfo> infos = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private final List<SegmentFacts> facts = new ArrayList<>();
    private final List<UnitSpan> spans = new ArrayList<>();
    private final Map<String, Integer> ordinalById = new HashMap<>();
    private final List<Integer> story = new ArrayList<>();
    private final Map<String, Tally> tallies = new HashMap<>();
    private final Map<Character.UnicodeScript, Integer> storyScripts = new EnumMap<>(Character.UnicodeScript.class);

    static IndexData build(final Document document) {
        final IndexBuilder builder = new IndexBuilder();
        document.units().forEach(builder::readUnit);
        return builder.finish(document);
    }

    private void readUnit(final Unit unit) {
        final int first = infos.size();
        unit.segments().forEach(segment -> readSegment(unit, segment));
        spans.addAll(Collections.nCopies(infos.size() - first, new UnitSpan(first, infos.size() - 1)));
    }

    private void readSegment(final Unit unit, final Segment segment) {
        final int ordinal = infos.size();
        infos.add(new SegmentInfo(ordinal, segment.id(), unit.id(), unit.role(), segment.kind(), unit.isAuxiliary()));
        texts.add(segment.masked());
        facts.add(new SegmentFacts(segment.declaredLanguage(), List.copyOf(segment.pairs())));
        ordinalById.put(segment.id(), ordinal);
        if (!unit.isAuxiliary() && unit.role() == UnitRole.BODY) {
            story.add(ordinal);
            LetterScripts.addTo(segment.masked(), storyScripts);
        }
        final Occurrences.Read read = Occurrences.read(segment.masked());
        for (int index = 0; index < read.words().size(); index++) {
            final Occurrences.Word word = read.words().get(index);
            final Posting posting = new Posting(
                    ordinal,
                    unit.id(),
                    word.start(),
                    word.sentence(),
                    word.capitalised(),
                    word.initial(),
                    Occurrences.isStandalone(read, index));
            tallies.computeIfAbsent(word.key(), _ -> new Tally()).add(word, posting);
        }
    }

    private IndexData finish(final Document document) {
        final Map<String, List<Posting>> postings = new HashMap<>();
        final Map<String, WordStats> stats = new HashMap<>();
        tallies.forEach((key, tally) -> {
            postings.put(key, List.copyOf(tally.postings));
            stats.put(key, tally.stats());
        });
        log.debug(
                "Indexed {} segments of {} units into {} word forms",
                infos.size(),
                document.units().size(),
                stats.size());
        final String language =
                document.declaredLang() != null ? document.declaredLang() : document.detectedSourceLang();
        return new IndexData(
                List.copyOf(infos),
                List.copyOf(texts),
                List.copyOf(facts),
                List.copyOf(spans),
                Map.copyOf(ordinalById),
                List.copyOf(story),
                postings,
                stats,
                language,
                LetterScripts.majorityOf(storyScripts).orElse(null));
    }

    /** One word form's postings and counts while the book is read. */
    private static final class Tally {
        private final List<Posting> postings = new ArrayList<>();
        private final WordTally counts = new WordTally();
        private int segments;
        private int lastOrdinal = -1;

        void add(final Occurrences.Word word, final Posting posting) {
            postings.add(posting);
            counts.add(word, posting.standalone());
            if (posting.ordinal() != lastOrdinal) {
                segments++;
                lastOrdinal = posting.ordinal();
            }
        }

        WordStats stats() {
            return new WordStats(
                    postings.size(),
                    counts.lowerCase(),
                    counts.capitalisedMidSentence(),
                    counts.sentenceInitial(),
                    counts.standalone(),
                    counts.lowerShare(),
                    segments,
                    postings.getFirst().ordinal());
        }
    }
}
