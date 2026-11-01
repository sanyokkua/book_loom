package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * Stores the genders the person gave in the Start question, each as the person's value, through the service's own
 * update so a chosen gender is shared with the character's other spellings; the first refusal ends the answers.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GenderAnswers {

    static Result<List<GlossaryEntry>> store(
            final GlossaryRepository glossary,
            final String projectId,
            final Map<String, Gender> answers,
            final Function<GlossaryEntry, Result<GlossaryEntry>> update) {
        return glossary.all(projectId).flatMap(held -> {
            final List<GlossaryEntry> stored = new ArrayList<>();
            for (final GlossaryEntry entry : held.stream()
                    .filter(asked -> answers.containsKey(asked.id()))
                    .toList()) {
                final Gender answer = Objects.requireNonNull(answers.get(entry.id()), "answer");
                log.debug("Gender answer entryId={} gender={}", entry.id(), answer);
                final Result<GlossaryEntry> updated = update.apply(entry.withGender(answer));
                if (updated.isErr()) {
                    return Result.err(Objects.requireNonNull(updated.error(), "error"));
                }
                stored.add(Objects.requireNonNull(updated.data(), "updated"));
            }
            log.info("Gender answers project={} asked={} stored={}", projectId, answers.size(), stored.size());
            return Result.ok(List.copyOf(stored));
        });
    }
}
