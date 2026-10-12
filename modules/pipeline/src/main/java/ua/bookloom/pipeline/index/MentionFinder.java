package ua.bookloom.pipeline.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds a name in the book as a run of up to three words separated by blanks only; a possessive ends a run, so
 * {@code Ashdown’s Garrick} is two names, and a trailing one is no part of the mention.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MentionFinder {

    private static final int MAX_NAME_WORDS = 3;
    private static final Pattern BLANKS = Pattern.compile("\\p{Zs}+");
    private static final Pattern APOSTROPHES = Pattern.compile("[’ʼ]");

    static List<Mention> find(final IndexData data, final Iterable<String> names, final Case mode) {
        final List<Mention> found = new ArrayList<>();
        for (final String name : names) {
            found.addAll(findOne(data, name, mode));
        }
        found.sort(Comparator.comparingInt(Mention::ordinal).thenComparingInt(Mention::start));
        return found;
    }

    private static List<Mention> findOne(final IndexData data, final String name, final Case mode) {
        final List<Occurrences.Word> wanted = Occurrences.read(name).words();
        if (wanted.isEmpty() || wanted.size() > MAX_NAME_WORDS) {
            log.debug("Name '{}' is {} words, not a run of one to {}", name, wanted.size(), MAX_NAME_WORDS);
            return List.of();
        }
        final List<Mention> found = new ArrayList<>();
        int previous = -1;
        for (final Posting posting :
                data.postings().getOrDefault(wanted.getFirst().key(), List.of())) {
            if (posting.ordinal() != previous) {
                previous = posting.ordinal();
                inSegment(data, name, wanted, mode, posting.ordinal(), found);
            }
        }
        return found;
    }

    private static void inSegment(
            final IndexData data,
            final String name,
            final List<Occurrences.Word> wanted,
            final Case mode,
            final int ordinal,
            final List<Mention> found) {
        final Occurrences.Read read = data.reads().get(ordinal);
        for (int start = 0; start + wanted.size() <= read.words().size(); start++) {
            if (isRun(read, start, wanted, mode)) {
                final Occurrences.Word first = read.words().get(start);
                final Occurrences.Word last = read.words().get(start + wanted.size() - 1);
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

    private static boolean isRun(
            final Occurrences.Read read, final int start, final List<Occurrences.Word> wanted, final Case mode) {
        for (int offset = 0; offset < wanted.size(); offset++) {
            final Occurrences.Word word = read.words().get(start + offset);
            if (!isSame(word, wanted.get(offset), mode) || (offset > 0 && !isJoined(read, start + offset))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJoined(final Occurrences.Read read, final int index) {
        final Occurrences.Word before = read.words().get(index - 1);
        return !before.possessive()
                && BLANKS.matcher(read.text()
                                .substring(before.end(), read.words().get(index).start()))
                        .matches();
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
