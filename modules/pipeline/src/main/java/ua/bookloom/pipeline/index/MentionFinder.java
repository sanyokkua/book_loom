package ua.bookloom.pipeline.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds a name in the book as a run of up to eight words separated by blanks only (two capitalised runs of three and
 * the joining word and determiner between them, 15i.N8); a possessive ends a run, so
 * {@code Ashdown’s Garrick} is two names, and a trailing one is no part of the mention. A name written with an
 * ampersand between two words ({@code Orrin & Vale}) is found where the book writes the same ampersand between them,
 * so a pair that is one name stays findable; a joint {@code and} is a word and needs no rule.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MentionFinder {

    // Two runs of three joined by a word and a determiner ("Keeper of the Grey Gate"), the longest name a scan makes.
    private static final int MAX_NAME_WORDS = 8;
    private static final Pattern BLANKS = Pattern.compile("\\p{Zs}+");
    private static final Pattern AMPERSAND_GAP = Pattern.compile("\\p{Zs}+&\\p{Zs}+");
    private static final Pattern APOSTROPHES = Pattern.compile("[’ʼ]");

    static List<Mention> find(final IndexData data, final Iterable<String> names, final Case mode) {
        final List<Mention> found = new ArrayList<>();
        for (final String name : names) {
            found.addAll(findOne(data, name, mode));
        }
        found.sort(Comparator.comparingInt(Mention::ordinal).thenComparingInt(Mention::start));
        return found;
    }

    /** The words a name asks for, and for each gap after the first word whether the name writes an ampersand there. */
    private record Wanted(List<Occurrences.Word> words, List<Boolean> ampersandBefore) {}

    private static List<Mention> findOne(final IndexData data, final String name, final Case mode) {
        final Wanted wanted = wantedBy(name);
        if (wanted.words().isEmpty() || wanted.words().size() > MAX_NAME_WORDS) {
            log.debug(
                    "Name '{}' is {} words, not a run of one to {}",
                    name,
                    wanted.words().size(),
                    MAX_NAME_WORDS);
            return List.of();
        }
        final List<Mention> found = new ArrayList<>();
        int previous = -1;
        for (final Posting posting :
                data.postings().getOrDefault(wanted.words().getFirst().key(), List.of())) {
            if (posting.ordinal() != previous) {
                previous = posting.ordinal();
                inSegment(data, name, wanted, mode, posting.ordinal(), found);
            }
        }
        return found;
    }

    private static Wanted wantedBy(final String name) {
        final Occurrences.Read read = Occurrences.read(name);
        final List<Boolean> ampersands = new ArrayList<>();
        for (int index = 0; index < read.words().size(); index++) {
            ampersands.add(index > 0
                    && AMPERSAND_GAP
                            .matcher(read.text()
                                    .substring(
                                            read.words().get(index - 1).end(),
                                            read.words().get(index).start()))
                            .matches());
        }
        return new Wanted(read.words(), ampersands);
    }

    private static void inSegment(
            final IndexData data,
            final String name,
            final Wanted wanted,
            final Case mode,
            final int ordinal,
            final List<Mention> found) {
        final Occurrences.Read read = data.read(ordinal);
        final int length = wanted.words().size();
        for (int start = 0; start + length <= read.words().size(); start++) {
            if (isRun(read, start, wanted, mode)) {
                final Occurrences.Word first = read.words().get(start);
                final Occurrences.Word last = read.words().get(start + length - 1);
                final int end = last.start() + last.text().length();
                found.add(new Mention(
                        name,
                        ordinal,
                        first.start(),
                        end,
                        first.sentence(),
                        Speech.isNarration(read.text(), first.start())));
            }
        }
    }

    private static boolean isRun(final Occurrences.Read read, final int start, final Wanted wanted, final Case mode) {
        for (int offset = 0; offset < wanted.words().size(); offset++) {
            final Occurrences.Word word = read.words().get(start + offset);
            if (!isSame(word, wanted.words().get(offset), mode)
                    || (offset > 0
                            && !isJoined(
                                    read,
                                    start + offset,
                                    wanted.ampersandBefore().get(offset)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJoined(final Occurrences.Read read, final int index, final boolean ampersand) {
        final Occurrences.Word before = read.words().get(index - 1);
        final String gap =
                read.text().substring(before.end(), read.words().get(index).start());
        return !before.possessive()
                && (ampersand ? AMPERSAND_GAP : BLANKS).matcher(gap).matches();
    }

    private static boolean isSame(final Occurrences.Word word, final Occurrences.Word wanted, final Case mode) {
        return switch (mode) {
            case SENSITIVE -> fold(word.text()).equals(fold(wanted.text()));
            case INSENSITIVE -> word.key().equals(wanted.key());
        };
    }

    private static String fold(final String word) {
        return APOSTROPHES.matcher(word).replaceAll("'");
    }
}
