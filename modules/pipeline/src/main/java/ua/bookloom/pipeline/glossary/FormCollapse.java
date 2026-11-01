package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * The names pass's collapse of a scanned form into one of two new names once both have suggested targets: the scan
 * keeps a form that fits two names without a target as an entry of its own ({@link ScanEntries}), and only the
 * suggestions show whether the names are one in the translation. The form then joins the name {@link LemmaCollapse}
 * chooses and its own entry goes; a form whose competitor is no entry, or whose competitors' targets differ, is left
 * as it is, and so is an entry the person settled.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FormCollapse {

    /**
     * Collapses every form entry the suggested targets allow.
     *
     * @param glossary the glossary the names pass edits; never null
     * @param projectId the project whose glossary is edited; never null
     * @param language the source language tag, or null when it is not known (nothing is collapsed)
     * @return how many form entries were folded into a name, or the first failed write's error
     */
    static Result<Integer> collapse(
            final GlossaryRepository glossary, final String projectId, @Nullable final String language) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(projectId, "projectId");
        return glossary.all(projectId).flatMap(held -> {
            final List<GlossaryEntry> forms =
                    held.stream().filter(entry -> isLooseForm(entry, language)).toList();
            final Map<String, Integer> namesPerRow = ScanEntries.namesPerRow(held, language);
            int collapsed = 0;
            for (final GlossaryEntry form : forms) {
                final Result<Boolean> done = fold(glossary, projectId, form, namesPerRow, language);
                if (done.isErr()) {
                    return Result.err(Objects.requireNonNull(done.error(), "error"));
                }
                collapsed += Boolean.TRUE.equals(done.data()) ? 1 : 0;
            }
            log.info("Form collapse project={} looseForms={} collapsed={}", projectId, forms.size(), collapsed);
            return Result.ok(collapsed);
        });
    }

    // A term that reads as an oblique case of a name and as no gendered nominative (every word also reads as itself,
    // indeclinable and of no gender), which nobody settled.
    private static boolean isLooseForm(final GlossaryEntry entry, @Nullable final String language) {
        final Set<NameForms.Reading> readings = NameForms.candidates(entry.term(), language);
        return !entry.locked()
                && entry.targetOrigin() != Origin.USER
                && entry.genderOrigin() != Origin.USER
                && entry.typeOrigin() != Origin.USER
                && readings.stream().anyMatch(reading -> !reading.nominative())
                && readings.stream().noneMatch(reading -> reading.nominative() && reading.gender() != Gender.UNKNOWN);
    }

    // Read again for each form, so a form folded before is no competitor's entry any more.
    private static Result<Boolean> fold(
            final GlossaryRepository glossary,
            final String projectId,
            final GlossaryEntry form,
            final Map<String, Integer> namesPerRow,
            @Nullable final String language) {
        return glossary.all(projectId).flatMap(held -> {
            final Map<String, String> targets = new LinkedHashMap<>();
            held.stream()
                    .filter(entry -> !entry.id().equals(form.id()) && entry.target() != null)
                    .forEach(entry -> targets.put(entry.term(), Objects.requireNonNull(entry.target(), "target")));
            final Optional<GlossaryEntry> winner = LemmaCollapse.collapse(form.term(), language, targets, namesPerRow)
                    .flatMap(name -> held.stream()
                            .filter(entry -> GlossaryKeys.of(entry.term()).equals(GlossaryKeys.of(name)))
                            .findFirst());
            log.debug("Form entry {} collapses: {}", form.id(), winner.isPresent());
            if (winner.isEmpty()) {
                return Result.ok(false);
            }
            log.trace("Form {} joins {}", form.term(), winner.get().term());
            final List<String> joined = new ArrayList<>(winner.get().forms());
            joined.addAll(form.names());
            return glossary.fold(projectId, form.id(), winner.get().withForms(joined))
                    .map(stored -> true);
        });
    }
}
