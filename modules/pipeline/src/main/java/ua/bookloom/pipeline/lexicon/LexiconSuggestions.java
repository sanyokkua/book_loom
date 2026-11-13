package ua.bookloom.pipeline.lexicon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.glossary.GlossaryModelScans;
import ua.bookloom.pipeline.glossary.KeyTermScan;

/**
 * The model's suggested renderings of recurring terms, asked through the glossary's suggestion call: the terms go out
 * as glossary entries of type term (of type title for a form of address, so the model and the pair's fixed renderings
 * both know it is one), and each answered target comes back as the term's suggestion, already without the stress marks
 * its language asks to remove (the suggestion call cleans every target it reads). A term the glossary already renders
 * under the same lower-case key ({@code houses} beside {@code Houses}) takes that rendering and is not asked.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LexiconSuggestions {

    /**
     * Gives every open term a suggestion: the glossary's rendering of the same key where it has one, else the model's.
     *
     * @param lexicon where the terms are stored
     * @param glossary where the names are held
     * @param modelScans the model calls
     * @param projectId the project
     * @param sourceLanguage the book's language tag, or null when not known
     * @param model the model to ask
     * @param progress the receiver of each call's start and finish
     * @return the lexicon as it now stands, or the first error
     */
    static Result<List<LexiconEntry>> run(
            final LexiconRepository lexicon,
            final GlossaryRepository glossary,
            final GlossaryModelScans modelScans,
            final String projectId,
            @Nullable final String sourceLanguage,
            final ChatModel model,
            final Consumer<JobEvent> progress) {
        return lexicon.all(projectId).flatMap(all -> {
            final List<LexiconEntry> open =
                    all.stream().filter(entry -> entry.established().isEmpty()).toList();
            log.info("Lexicon suggestions project={} terms={} open={}", projectId, all.size(), open.size());
            if (open.isEmpty()) {
                return Result.ok(all);
            }
            return glossary.all(projectId).flatMap(names -> {
                final Map<String, String> held = held(open, names);
                final List<LexiconEntry> unasked = open.stream()
                        .filter(term -> !held.containsKey(LexiconEntry.keyOf(term.term())))
                        .toList();
                return storeHeld(lexicon, projectId, open, held)
                        .flatMap(stored -> unasked.isEmpty()
                                ? lexicon.all(projectId)
                                : modelScans
                                        .suggestOnto(
                                                projectId,
                                                asGlossary(projectId, unasked, sourceLanguage),
                                                model,
                                                progress)
                                        .flatMap(answered -> store(lexicon, projectId, unasked, answered)));
            });
        });
    }

    /**
     * The terms as the glossary entries the suggestion call reads, in their order.
     *
     * @param projectId the project
     * @param entries the terms to ask about
     * @param sourceLanguage the book's language tag, which knows its forms of address; null when not known
     * @return one entry per term, of type title for a form of address and term for any other
     */
    static List<GlossaryEntry> asGlossary(
            final String projectId, final List<LexiconEntry> entries, @Nullable final String sourceLanguage) {
        return entries.stream()
                .map(entry -> GlossaryEntry.of(
                        GlossaryIds.of(projectId, entry.term()),
                        projectId,
                        entry.term(),
                        null,
                        KeyTermScan.isTitle(entry.term(), sourceLanguage) ? TermType.TITLE : TermType.TERM,
                        Gender.UNKNOWN,
                        false))
                .toList();
    }

    /**
     * The renderings the glossary already holds for terms of the list, by the term's lexicon key, written in the term's
     * own case. A person's name is left out: a nickname beside a common word is not its rendering.
     *
     * @param open the terms to ask about
     * @param names the project's glossary
     * @return the rendering to reuse for each term that has one; never null
     */
    static Map<String, String> held(final List<LexiconEntry> open, final List<GlossaryEntry> names) {
        final Map<String, String> byKey = new HashMap<>();
        names.stream()
                .filter(name -> name.type() != TermType.CHARACTER)
                .filter(name -> name.target() != null && !name.target().isBlank())
                .forEach(name -> byKey.putIfAbsent(LexiconEntry.keyOf(name.term()), name.target()));
        final Map<String, String> held = new HashMap<>();
        open.forEach(entry -> {
            final String rendering = byKey.get(LexiconEntry.keyOf(entry.term()));
            if (rendering != null) {
                held.put(LexiconEntry.keyOf(entry.term()), inCaseOf(entry.term(), rendering));
            }
        });
        log.debug("Lexicon suggestions reuse {} glossary renderings of {} open terms", held.size(), open.size());
        return held;
    }

    private static String inCaseOf(final String term, final String rendering) {
        final int first = rendering.codePointAt(0);
        final int cased = Character.isLowerCase(term.codePointAt(0))
                ? Character.toLowerCase(first)
                : Character.toTitleCase(first);
        return new StringBuilder()
                .appendCodePoint(cased)
                .append(rendering.substring(Character.charCount(first)))
                .toString();
    }

    /**
     * Stores each answered target as its term's suggestion.
     *
     * @param lexicon where the terms are stored
     * @param projectId the project
     * @param open the terms asked, in the order of {@code answered}
     * @param answered the entries as answered, one per asked term
     * @return the lexicon as it now stands, or the first store error
     */
    static Result<List<LexiconEntry>> store(
            final LexiconRepository lexicon,
            final String projectId,
            final List<LexiconEntry> open,
            final List<GlossaryEntry> answered) {
        int stored = 0;
        for (int i = 0; i < open.size(); i++) {
            final String target = answered.get(i).target();
            if (target != null && !target.isBlank()) {
                final Result<Optional<LexiconEntry>> updated = suggested(lexicon, projectId, open.get(i), target);
                if (updated.isErr()) {
                    return Result.err(Objects.requireNonNull(updated.error(), "error"));
                }
                stored++;
            }
        }
        log.debug("Lexicon suggestions project={} asked={} stored={}", projectId, open.size(), stored);
        return lexicon.all(projectId);
    }

    /**
     * Stores each held rendering as its term's suggestion.
     *
     * @param lexicon where the terms are stored
     * @param projectId the project
     * @param open the terms to ask about
     * @param held the renderings to reuse, by lexicon key ({@link #held})
     * @return whether any was stored, or the first store error
     */
    static Result<Boolean> storeHeld(
            final LexiconRepository lexicon,
            final String projectId,
            final List<LexiconEntry> open,
            final Map<String, String> held) {
        for (final LexiconEntry term : open) {
            final String rendering = held.get(LexiconEntry.keyOf(term.term()));
            if (rendering != null) {
                final Result<Optional<LexiconEntry>> stored = suggested(lexicon, projectId, term, rendering);
                if (stored.isErr()) {
                    return Result.err(Objects.requireNonNull(stored.error(), "error"));
                }
            }
        }
        return Result.ok(!held.isEmpty());
    }

    /**
     * Stores a rendering as one term's suggestion.
     *
     * @param lexicon where the terms are stored
     * @param projectId the project
     * @param term the term
     * @param rendering the non-blank rendering
     * @return the stored term, or the store error
     */
    static Result<Optional<LexiconEntry>> suggested(
            final LexiconRepository lexicon, final String projectId, final LexiconEntry term, final String rendering) {
        return lexicon.update(projectId, term.term(), found -> found.withSuggested(rendering));
    }
}
