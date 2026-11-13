package ua.bookloom.pipeline.glossary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * The review flags of every held target ({@code specs/glossary/spec.md} "Flag a target that is not a dictionary form"):
 * computed by {@link TargetValidator} from the target language's file each time they are asked for, never stored, so a
 * changed target or file changes them at once. Only the flags are read here; a refusal is Accept all's to report.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewFlags {

    static Result<Map<String, List<TargetIssue>>> of(
            final GlossaryRepository glossary,
            final String projectId,
            @Nullable final BookBrief brief,
            @Nullable final String analysisLanguage) {
        final String targetLanguage = brief == null ? null : brief.targetLanguage();
        if (brief == null || targetLanguage == null) {
            log.debug("Review flags project={}: no target language is chosen, nothing is flagged", projectId);
            return Result.ok(Map.of());
        }
        return glossary.all(projectId).map(held -> {
            final TargetValidator validator = TargetValidator.of(
                    targetLanguage,
                    analysisLanguage,
                    brief.names(),
                    new TargetContext(held, Map.of()),
                    brief.conventions());
            final Map<String, List<TargetIssue>> flags = new LinkedHashMap<>();
            held.stream().filter(ReviewFlags::hasTarget).forEach(entry -> {
                final List<TargetIssue> found =
                        TargetIssues.flagsOf(validator.validate(entry, String.valueOf(entry.target())));
                if (!found.isEmpty()) {
                    flags.put(entry.id(), found);
                }
            });
            log.debug("Review flags project={} targets read={} flagged={}", projectId, held.size(), flags.size());
            return Map.copyOf(flags);
        });
    }

    private static boolean hasTarget(final GlossaryEntry entry) {
        return entry.target() != null && !entry.target().isBlank();
    }
}
