package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.util.lang.LanguageTags;

/** Finds where a segment's protected spans lie in its masked text, before any of them is replaced. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SpanFinder {

    /** One span found in the masked text: {@code [start, end)}, what it restores to, and the gate it belongs to. */
    record Found(
            int start,
            int end,
            String restored,
            CheckName check,
            @Nullable String term) {

        boolean overlaps(final Found other) {
            return start < other.end && other.start < end;
        }
    }

    /**
     * The runs to keep verbatim, under Keep as-is only: the whole segment when its block declares a language that
     * normalizes to a tag other than both the source's and the book's own; otherwise each inline pair whose own declared
     * language differs from the source's, both being recognized languages. A pair inside another kept pair is part of
     * that one.
     *
     * @param bookLanguage the language the book declares of itself, or null when unknown: a block declaring it is the
     *     book's own text even when the brief names another source
     */
    static List<Found> keptRuns(
            final Segment segment,
            @Nullable final String sourceLanguage,
            @Nullable final String bookLanguage,
            final ForeignPassagePolicy policy) {
        final Optional<String> source = LanguageTags.normalize(sourceLanguage);
        if (policy != ForeignPassagePolicy.KEEP || source.isEmpty()) {
            log.debug(
                    "Kept runs skipped segment={} policy={} sourceRecognized={}",
                    segment.id(),
                    policy,
                    source.isPresent());
            return List.of();
        }
        if (isForeignBlock(segment, source.get(), bookLanguage)) {
            return List.of(new Found(0, segment.masked().length(), segment.masked(), CheckName.KEPT_RUN, null));
        }
        final List<Found> found = new ArrayList<>();
        for (final PlaceholderPair pair : segment.pairs()) {
            if (isForeign(pair.language(), source.get())) {
                runOf(segment.masked(), pair).ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparingInt(Found::start)
                .thenComparing(Comparator.comparingInt(Found::end).reversed()));
        return withoutNested(found);
    }

    /**
     * Whether the segment's block declares a foreign language: a Latin paragraph {@code <p xml:lang="la">} in an English
     * book. A block that only inherits the book's own declaration from {@code <body>} is never foreign, so a brief whose
     * source names another language than the book does not keep the whole book untranslated.
     */
    static boolean isForeignBlock(
            final Segment segment, final String normalizedSource, @Nullable final String bookLanguage) {
        final Optional<String> declared = LanguageTags.normalize(segment.declaredLanguage());
        final Optional<String> book = LanguageTags.normalize(bookLanguage);
        final boolean foreign = declared.isPresent()
                && !declared.get().equals(normalizedSource)
                && !declared.equals(book)
                && !segment.masked().isBlank();
        log.debug(
                "Foreign block check segment={} declared={} source={} book={} foreign={}",
                segment.id(),
                declared.orElse(null),
                normalizedSource,
                book.orElse(null),
                foreign);
        return foreign;
    }

    private static boolean isForeign(@Nullable final String declared, final String normalizedSource) {
        return LanguageTags.normalize(declared)
                .filter(tag -> !tag.equals(normalizedSource))
                .isPresent();
    }

    private static Optional<Found> runOf(final String text, final PlaceholderPair pair) {
        final int open = text.indexOf(pair.open());
        final int close =
                open < 0 ? -1 : text.indexOf(pair.close(), open + pair.open().length());
        if (close < 0) {
            return Optional.empty();
        }
        final int end = close + pair.close().length();
        return Optional.of(new Found(open, end, text.substring(open, end), CheckName.KEPT_RUN, null));
    }

    private static List<Found> withoutNested(final List<Found> sortedRuns) {
        final List<Found> kept = new ArrayList<>();
        for (final Found run : sortedRuns) {
            if (kept.isEmpty() || !kept.getLast().overlaps(run)) {
                kept.add(run);
            }
        }
        return kept;
    }

    /**
     * Whole-word, case-sensitive matches of each locked entry that has a non-blank target, outside every kept run
     * and every existing token; longest term first, then by position, kept greedily without overlap.
     */
    static List<Found> lockedTerms(final String text, final List<Found> keptRuns, final List<GlossaryEntry> glossary) {
        final List<Found> blocked = new ArrayList<>(keptRuns);
        blocked.addAll(tokenRanges(text));
        final List<Found> candidates = new ArrayList<>();
        for (final GlossaryEntry entry : glossary) {
            final String target = entry.target();
            if (entry.locked()
                    && target != null
                    && !target.isBlank()
                    && !entry.term().isBlank()) {
                candidates.addAll(matchesOf(text, entry.term(), target));
            }
        }
        candidates.sort(Comparator.comparingInt((Found found) -> found.end() - found.start())
                .reversed()
                .thenComparingInt(Found::start));
        final List<Found> accepted = new ArrayList<>();
        for (final Found candidate : candidates) {
            if (blocked.stream().noneMatch(candidate::overlaps)) {
                accepted.add(candidate);
                blocked.add(candidate);
            }
        }
        return accepted;
    }

    private static List<Found> matchesOf(final String text, final String term, final String target) {
        final Matcher matcher = WholeWord.pattern(term).matcher(text);
        final List<Found> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new Found(matcher.start(), matcher.end(), target, CheckName.LOCKED_TERM, term));
        }
        return matches;
    }

    private static List<Found> tokenRanges(final String text) {
        final Matcher matcher = Tokens.matcher(text);
        final List<Found> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new Found(matcher.start(), matcher.end(), matcher.group(), CheckName.PLACEHOLDER, null));
        }
        return ranges;
    }
}
