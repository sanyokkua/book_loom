package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What {@link CooccurrenceLearner} counts for one tracked term: the forms that name it (its stem group), how many
 * decided segments named it, how many of those in lower case, and the target stems met beside it. It is confined to
 * the learner's thread, which is why its counters are plain fields.
 */
final class TrackedTerm {

    final List<String> forms;
    final boolean commonWord;
    final boolean name;
    final boolean capitals;
    final Pattern lowerCaseForm;
    final Map<String, Integer> withStem = new HashMap<>();
    int occurrences;
    int lowerCaseSegments;

    TrackedTerm(
            final String term,
            final Collection<String> others,
            final boolean commonWord,
            final boolean name,
            final boolean title) {
        final List<String> all = new ArrayList<>();
        all.add(term);
        others.stream().filter(form -> !form.isBlank()).forEach(all::add);
        this.forms = List.copyOf(all);
        this.commonWord = commonWord;
        this.name = name;
        this.capitals = name || title;
        final String alternatives = forms.stream()
                .map(form -> Pattern.quote(form.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining("|"));
        this.lowerCaseForm =
                Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?:['’ʼ]?s|es)?(?![\\p{L}\\p{N}])");
    }
}
