package ua.bookloom.pipeline.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Chooses which mentions to show: the first, then from each of the equal stretches the rest is cut into the middle
 * narration mention (the middle mention when the stretch is all speech); a sentence already shown is not shown twice.
 * A caller may prefer some mentions — the name review prefers one whose window gives the code a pronoun of the name —
 * and then the first preferred mention of a stretch's pool is shown instead of its middle.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowPicker {

    static List<Mention> choose(final List<Mention> mentions, final int count, final Predicate<Mention> preferred) {
        if (mentions.isEmpty() || count <= 0) {
            return List.of();
        }
        final List<Mention> chosen = new ArrayList<>();
        final Set<Long> sentences = new HashSet<>();
        chosen.add(mentions.getFirst());
        sentences.add(sentenceKey(mentions.getFirst()));
        final List<Mention> rest = mentions.subList(1, mentions.size());
        final int stretches = count - 1;
        for (int stretch = 0; stretch < stretches && !rest.isEmpty(); stretch++) {
            final List<Mention> part =
                    rest.subList(stretch * rest.size() / stretches, (stretch + 1) * rest.size() / stretches);
            pick(part, sentences, preferred).ifPresent(pick -> {
                chosen.add(pick);
                sentences.add(sentenceKey(pick));
            });
        }
        chosen.sort(Comparator.comparingLong(WindowPicker::sentenceKey).thenComparingInt(Mention::start));
        return chosen;
    }

    private static Optional<Mention> pick(
            final List<Mention> part, final Set<Long> shown, final Predicate<Mention> preferred) {
        final List<Mention> unseen =
                part.stream().filter(m -> !shown.contains(sentenceKey(m))).toList();
        final List<Mention> narrated =
                unseen.stream().filter(Mention::narration).toList();
        final List<Mention> pool = narrated.isEmpty() ? unseen : narrated;
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pool.stream().filter(preferred).findFirst().orElse(pool.get(pool.size() / 2)));
    }

    private static long sentenceKey(final Mention mention) {
        return ((long) mention.ordinal() << Integer.SIZE) | mention.sentence();
    }
}
