package ua.bookloom.pipeline.lexicon;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** How many segments carry each stem, and the surface forms that stem was seen as. */
final class Vocabulary {

    private final Map<String, Integer> segmentsWithStem = new HashMap<>();
    private final Map<String, Map<String, Integer>> surfaceForms = new HashMap<>();

    /** Counts each stem once for the segment and each surface form once per use; answers the segment's stems. */
    Set<String> count(final List<TargetWords.Word> words) {
        final Set<String> stems = new LinkedHashSet<>();
        for (final TargetWords.Word word : words) {
            surfaceForms.computeIfAbsent(word.stem(), key -> new HashMap<>()).merge(word.surface(), 1, Integer::sum);
            stems.add(word.stem());
        }
        stems.forEach(stem -> segmentsWithStem.merge(stem, 1, Integer::sum));
        return stems;
    }

    int segmentsWith(final String stem) {
        return segmentsWithStem.getOrDefault(stem, 0);
    }

    Map<String, Integer> formsOf(final String stem) {
        return surfaceForms.getOrDefault(stem, Map.of());
    }
}
