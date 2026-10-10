package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.glossary.PronounGender;
import ua.bookloom.pipeline.glossary.SuggestTargets;
import ua.bookloom.pipeline.glossary.TermReview;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.setup.SetupAssistantImpl;

/**
 * Asks a model for its judgement of one book the way the app does: the book is imported through the real project
 * service with nothing stated about it, the production {@link SetupAssistantImpl} suggests the Book Brief from its
 * samples, and the production {@link TermReview} reviews a glossary that holds the gold's main characters as a scan
 * leaves them (untyped and gender unknown, unless the book's pronouns suggest one), with the code's pronoun and
 * first-name seeding after it. What the model and
 * the code concluded is returned for scoring; nothing is judged here.
 */
@Slf4j
final class JudgementRunner {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StageProbe probe;

    JudgementRunner(final ChatModel model) {
        this.probe = new StageProbe(Objects.requireNonNull(model, "model"));
    }

    StageProbe probe() {
        return probe;
    }

    /** The model's judgement of the book at {@code book}, whose gold names its languages and main characters. */
    Judgement judge(final String id, final Path book, final JudgementGold gold) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(gold, "gold");
        log.info("Judgement eval book={} source={} target={}", id, gold.sourceLanguage(), gold.targetLanguage());
        final BookBrief brief = SequenceJobs.brief(
                gold.sourceLanguage(), gold.targetLanguage(), QualityDial.FAST, Narrator.unspecified());
        final Prepared prepared = SequenceJobs.importBook(book, brief);
        final int mark = probe.mark();
        final Result<BriefSuggestion> suggested = assistant(prepared).suggestBrief(prepared.projectId(), probe);
        seed(prepared, gold);
        final Result<GlossaryReviewReport> reviewed = review(prepared, brief, gold);
        final Judgement judgement = new Judgement(
                id,
                suggested.data(),
                errorOf(suggested),
                reviewed.data() == null ? Map.of() : byTerm(Objects.requireNonNull(reviewed.data())),
                errorOf(reviewed),
                probe.since(mark).calls());
        log.debug(
                "Judgement eval book={} briefError={} reviewError={} calls={}",
                id,
                judgement.briefError(),
                judgement.reviewError(),
                judgement.calls());
        return judgement;
    }

    private Result<GlossaryReviewReport> review(
            final Prepared prepared, final BookBrief brief, final JudgementGold gold) {
        return new TermReview(TEMPLATES, MAPPER, prepared.stores().glossary(), new SuggestTargets(TEMPLATES, MAPPER))
                .review(
                        prepared.projectId(),
                        FrequencyScan.storyText(prepared.document()),
                        StageFrames.frame(brief, gold.sourceLanguage(), gold.targetLanguage()),
                        brief.names(),
                        probe.calls(gold.targetLanguage()));
    }

    private static SetupAssistantImpl assistant(final Prepared prepared) {
        return new SetupAssistantImpl(
                prepared.stores().projects(),
                prepared.stores().segments(),
                prepared.stores().glossary(),
                prepared.stores().openProjects(),
                TEMPLATES,
                MAPPER,
                Clock.systemUTC());
    }

    // The scan seeds its proposals from the book's pronouns before storing them, as the app's scan does.
    private static void seed(final Prepared prepared, final JudgementGold gold) {
        final String project = prepared.projectId();
        final List<String> texts = Tokens.visibleTexts(FrequencyScan.storyText(prepared.document()));
        gold.characters()
                .forEach(character -> prepared.stores()
                        .glossary()
                        .add(PronounGender.seeded(
                                new GlossaryEntry(
                                        GlossaryIds.of(project, character.term()),
                                        project,
                                        character.term(),
                                        null,
                                        TermType.OTHER,
                                        Gender.UNKNOWN,
                                        false),
                                texts,
                                gold.sourceLanguage())));
    }

    private static Map<String, GlossaryEntry> byTerm(final GlossaryReviewReport report) {
        final Map<String, GlossaryEntry> entries = new LinkedHashMap<>();
        report.entries().forEach(entry -> entries.put(key(entry.term()), entry));
        return entries;
    }

    static String key(final String term) {
        return term.strip().toLowerCase(Locale.ROOT);
    }

    private static @Nullable String errorOf(final Result<?> result) {
        return result.isErr() ? Objects.requireNonNull(result.error()).code().name() : null;
    }

    /**
     * One judgement of one book.
     *
     * @param book the book's id
     * @param brief the suggested brief, or null when the suggestion failed
     * @param briefError the failure's code, or null
     * @param entries the glossary after the review by lower-case term; empty when the review failed
     * @param reviewError the failure's code, or null
     * @param calls the model calls both steps sent
     */
    record Judgement(
            String book,
            @Nullable BriefSuggestion brief,
            @Nullable String briefError,
            Map<String, GlossaryEntry> entries,
            @Nullable String reviewError,
            int calls) {

        /** Copies the entries. */
        Judgement {
            Objects.requireNonNull(book, "book");
            entries = Map.copyOf(entries);
        }
    }
}
