package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.TermReading.Reading;

/**
 * The once-per-name question about a name whose meaning the book plays on (design D5). Only the names pass marks a
 * name as asked ({@link MeaningChoice#PENDING}), so nothing is ever asked during a run; a name that already holds a
 * choice is never marked again, and every answer — closing the question included — stores a choice, which is what
 * makes the question once per name.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MeaningQuestions {

    /**
     * Marks the names to ask about: a character or a place whose reading cited a window that explains or plays on its
     * meaning, with no choice yet and a target nobody accepted, under the Transliterate policy (under Translate such a
     * name is already rendered by its meaning, and under Keep original it is kept).
     *
     * @param glossary the glossary the names pass edits; never null
     * @param projectId the project whose glossary is edited; never null
     * @param readings the names step's readings, by entry id; never null
     * @param policy the Book Brief's name policy; never null
     * @return how many names were marked, or the first failed write's error
     */
    static Result<Integer> mark(
            final GlossaryRepository glossary,
            final String projectId,
            final Map<String, Reading> readings,
            final NamePolicy policy) {
        Objects.requireNonNull(readings, "readings");
        Objects.requireNonNull(policy, "policy");
        if (policy != NamePolicy.TRANSLITERATE) {
            log.debug("Meaning questions: policy {} asks nothing", policy);
            return Result.ok(0);
        }
        return glossary.all(projectId).flatMap(held -> {
            final List<GlossaryEntry> asked = held.stream()
                    .filter(entry -> isAsked(entry, readings.get(entry.id())))
                    .toList();
            for (final GlossaryEntry entry : asked) {
                log.debug("Meaning question pending for entry {}", entry.id());
                log.trace("Meaning question pending for {}", entry.term());
                final Result<GlossaryEntry> stored = glossary.update(entry.withMeaningChoice(MeaningChoice.PENDING));
                if (stored.isErr()) {
                    return Result.err(Objects.requireNonNull(stored.error(), "error"));
                }
            }
            log.info("Meaning questions project={} pending={}", projectId, asked.size());
            return Result.ok(asked.size());
        });
    }

    private static boolean isAsked(final GlossaryEntry entry, @Nullable final Reading reading) {
        final boolean isName = entry.type() == TermType.CHARACTER || entry.type() == TermType.PLACE;
        final boolean isTargetOpen = entry.targetOrigin() == null || entry.targetOrigin() == Origin.LIKELY;
        return reading != null
                && reading.explainsName()
                && isName
                && isTargetOpen
                && !entry.locked()
                && entry.meaningChoice() == null;
    }

    /**
     * The names still waiting for the question.
     *
     * @param glossary the stored glossary; never null
     * @param projectId the project; never null
     * @return the entries marked {@link MeaningChoice#PENDING}, in glossary order, or the read's error
     */
    static Result<List<GlossaryEntry>> pending(final GlossaryRepository glossary, final String projectId) {
        return glossary.all(projectId).map(held -> {
            final List<GlossaryEntry> pending = held.stream()
                    .filter(entry -> entry.meaningChoice() == MeaningChoice.PENDING)
                    .toList();
            log.debug("Meaning questions project={} pending={}", projectId, pending.size());
            return pending;
        });
    }

    /**
     * Stores the person's answers, each through {@link #answered}.
     *
     * @param glossary the stored glossary; never null
     * @param projectId the project; never null
     * @param answers the answer per entry id; an id the glossary no longer holds is skipped
     * @return the entries the answers changed, as stored, or the first failed access's error
     */
    static Result<List<GlossaryEntry>> answer(
            final GlossaryRepository glossary, final String projectId, final Map<String, MeaningChoice> answers) {
        return glossary.all(projectId).flatMap(held -> {
            final List<GlossaryEntry> answered = held.stream()
                    .filter(entry -> answers.containsKey(entry.id()))
                    .map(entry -> answered(entry, Objects.requireNonNull(answers.get(entry.id()), "answer")))
                    .toList();
            log.info("Meaning answers project={} asked={} stored={}", projectId, answers.size(), answered.size());
            return glossary.putAll(projectId, answered);
        });
    }

    /**
     * The entry as the person's answer leaves it. Transcribe and transcribe-and-gloss keep the target and the note;
     * translate drops a target nobody accepted, so the next suggestion call asks for the name by its meaning; an answer
     * of {@link MeaningChoice#PENDING} (the question closed unanswered) is transcribe.
     *
     * @param entry the entry asked about; never null
     * @param choice the answer; never null
     * @return the entry with the choice stored
     */
    static GlossaryEntry answered(final GlossaryEntry entry, final MeaningChoice choice) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(choice, "choice");
        final MeaningChoice stored = choice == MeaningChoice.PENDING ? MeaningChoice.TRANSCRIBE : choice;
        final boolean dropsTarget = stored == MeaningChoice.TRANSLATE && entry.isSuggested();
        log.debug("Meaning answer for entry {}: {} dropsTarget={}", entry.id(), stored, dropsTarget);
        final GlossaryEntry chosen = entry.withMeaningChoice(stored);
        return dropsTarget ? chosen.withTarget(null) : chosen;
    }
}
