package ua.bookloom.pipeline.glossary;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Collapses a written form that could be a case of two or more names into one of them when nothing would change in the
 * translation: every competing name has a target and all the targets are the same ({@code Градовского} of
 * {@code Градовский} or {@code Градовской}, both {@code Градовський}). The form is then the name of the class more of
 * the book's names decline by, a tie going to the row the language file lists first. Names with different targets are
 * different people, so such a form is never collapsed; nor is one whose competitor has no target yet.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LemmaCollapse {

    /**
     * The one name a contested form belongs to.
     *
     * @param form the non-null written form
     * @param language the source language tag, or null when it is not known (nothing is collapsed)
     * @param targets the known targets by name, as the names are written; never null
     * @param namesPerRow how many of the book's names decline by each row ({@code ending|gender}); never null, a
     *     missing row counts zero
     * @return the name the form collapses to, or empty when it fits one name only, a competitor has no target or the
     *     targets differ
     */
    static Optional<String> collapse(
            final String form,
            @Nullable final String language,
            final Map<String, String> targets,
            final Map<String, Integer> namesPerRow) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(namesPerRow, "namesPerRow");
        final Map<String, NameForms.Declension> competing = new LinkedHashMap<>();
        NameForms.declensions(form, language).stream()
                .filter(declension -> !declension.nominative())
                .forEach(declension -> competing.putIfAbsent(GlossaryKeys.of(declension.lemma()), declension));
        if (competing.size() < 2) {
            return Optional.empty();
        }
        final Map<String, String> byKey = new LinkedHashMap<>();
        targets.forEach((name, target) -> byKey.put(GlossaryKeys.of(name), GlossaryKeys.of(target)));
        final List<String> found = competing.keySet().stream().map(byKey::get).toList();
        final boolean same = found.stream().allMatch(Objects::nonNull)
                && found.stream().distinct().count() == 1;
        log.debug("Contested form of {} names: targets known and equal {}", competing.size(), same);
        if (!same) {
            return Optional.empty();
        }
        return competing.values().stream()
                .min(Comparator.comparingInt(
                                (NameForms.Declension declension) -> -namesPerRow.getOrDefault(declension.row(), 0))
                        .thenComparingInt(NameForms.Declension::order))
                .map(NameForms.Declension::lemma);
    }
}
