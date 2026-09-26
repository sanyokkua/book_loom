package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ibm.icu.text.MessagePattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Every catalogue entry is a valid ICU pattern, and a counted one carries every plural form its language has. */
class IcuPatternTest {

    private static final Set<String> UKRAINIAN_FORMS = Set.of("one", "few", "many", "other");
    private static final Set<String> ENGLISH_FORMS = Set.of("one", "other");

    static Stream<Arguments> everyEntry() {
        return Stream.of("en", "uk")
                .flatMap(language -> Catalogues.load(language).entrySet().stream()
                        .map(entry -> Arguments.of(language, entry.getKey(), entry.getValue())));
    }

    // IF a pattern does not parse (unbalanced brace, bad argument), THEN it would throw at display time; fail here.
    @ParameterizedTest(name = "[{0}] {1} parses")
    @MethodSource("everyEntry")
    void pattern_everyCatalogueEntry_parsesAsIcu(final String language, final String key, final String pattern) {
        assertThatCode(() -> new MessagePattern(pattern)).doesNotThrowAnyException();
    }

    // IF a plural nested in another plural appeared, THEN the simple selector scan below would no longer be sound.
    @ParameterizedTest(name = "[{0}] {1} has no nested plural")
    @MethodSource("everyEntry")
    void pattern_everyCatalogueEntry_hasNoNestedPlural(final String language, final String key, final String pattern) {
        assertThat(nestedPluralCount(new MessagePattern(pattern))).isZero();
    }

    // IF a Ukrainian counted message misses one, few, many or other, THEN some numbers fall into the wrong form.
    @ParameterizedTest(name = "[uk] {1} defines every Ukrainian form")
    @MethodSource("ukrainianEntries")
    void pattern_ukrainianPlural_definesOneFewManyOther(final String language, final String key, final String pattern) {
        assertThat(pluralSelectorSets(new MessagePattern(pattern)))
                .allSatisfy(selectors -> assertThat(selectors).containsAll(UKRAINIAN_FORMS));
    }

    // IF an English counted message misses one or other, THEN a number falls to no form.
    @ParameterizedTest(name = "[en] {1} defines one and other")
    @MethodSource("englishEntries")
    void pattern_englishPlural_definesOneAndOther(final String language, final String key, final String pattern) {
        assertThat(pluralSelectorSets(new MessagePattern(pattern)))
                .allSatisfy(selectors -> assertThat(selectors).containsAll(ENGLISH_FORMS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void catalogue_remainingSegments_isACountedPluralMessage(final String language) {
        // IF the counted exemplar lost its plural, THEN the two form checks above would pass vacuously for it.
        final String pattern = Catalogues.load(language).get(MessageKey.TRANSLATING_SEGMENTS_REMAINING.key());

        assertThat(pluralSelectorSets(new MessagePattern(pattern))).hasSize(1);
    }

    static Stream<Arguments> ukrainianEntries() {
        return everyEntry().filter(arguments -> "uk".equals(arguments.get()[0]));
    }

    static Stream<Arguments> englishEntries() {
        return everyEntry().filter(arguments -> "en".equals(arguments.get()[0]));
    }

    /** One selector set per plural argument; explicit {@code =N} selectors are not forms and are left out. */
    private static List<Set<String>> pluralSelectorSets(final MessagePattern pattern) {
        final List<Set<String>> result = new ArrayList<>();
        for (int i = 0; i < pattern.countParts(); i++) {
            if (isPluralStart(pattern, i)) {
                result.add(selectorsOf(pattern, i));
            }
        }
        return result;
    }

    private static Set<String> selectorsOf(final MessagePattern pattern, final int pluralStart) {
        final Set<String> selectors = new java.util.HashSet<>();
        final int limit = pattern.getLimitPartIndex(pluralStart);
        int i = pluralStart + 1;
        while (i < limit) {
            final MessagePattern.Part part = pattern.getPart(i);
            if (part.getType() == MessagePattern.Part.Type.ARG_START) {
                i = pattern.getLimitPartIndex(i);
            } else {
                if (part.getType() == MessagePattern.Part.Type.ARG_SELECTOR) {
                    selectors.add(pattern.getSubstring(part));
                }
                i++;
            }
        }
        return selectors;
    }

    private static int nestedPluralCount(final MessagePattern pattern) {
        int nested = 0;
        for (int i = 0; i < pattern.countParts(); i++) {
            if (isPluralStart(pattern, i)) {
                final int limit = pattern.getLimitPartIndex(i);
                for (int j = i + 1; j < limit; j++) {
                    if (isPluralStart(pattern, j)) {
                        nested++;
                    }
                }
            }
        }
        return nested;
    }

    private static boolean isPluralStart(final MessagePattern pattern, final int index) {
        final MessagePattern.Part part = pattern.getPart(index);
        return part.getType() == MessagePattern.Part.Type.ARG_START
                && part.getArgType() == MessagePattern.ArgType.PLURAL;
    }
}
