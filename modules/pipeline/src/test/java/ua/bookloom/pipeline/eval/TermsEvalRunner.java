package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.eval.StageCases.HeldTerm;
import ua.bookloom.pipeline.eval.StageCases.TermVerdict;
import ua.bookloom.pipeline.eval.StageCases.TermsCase;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.glossary.SuggestTargets;
import ua.bookloom.pipeline.glossary.TermChoice;
import ua.bookloom.pipeline.glossary.TermChoice.Choice;
import ua.bookloom.pipeline.glossary.TermReview;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Runs each case through the production {@link TermChoice} (the recurring-term choice, TERM_CHOICE) and the production
 * {@link TermReview} (the glossary review, REVIEW_TERMS verdicts): everyday words must be dropped, jargon and names
 * kept. A term the choice leaves undecided counts as refused, and is not a keep.
 */
@Slf4j
final class TermsEvalRunner {

    private static final String PROJECT = "eval";
    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StageProbe probe;

    TermsEvalRunner(final ChatModel model) {
        this.probe = new StageProbe(Objects.requireNonNull(model, "model"));
    }

    StageProbe probe() {
        return probe;
    }

    List<StageRow> run(final List<TermsCase> cases) {
        final List<StageRow> rows = new ArrayList<>();
        for (final TermsCase termsCase : cases) {
            log.info("Terms eval case={}", termsCase.id());
            rows.add(choice(termsCase));
            rows.add(review(termsCase));
        }
        return rows;
    }

    private StageRow choice(final TermsCase termsCase) {
        final CallFrame frame = frameOf(termsCase);
        final int mark = probe.mark();
        final Result<Choice> chosen = new TermChoice(TEMPLATES, MAPPER)
                .choose(
                        termsCase.terms().stream().map(TermVerdict::term).toList(),
                        StageFrames.segments(termsCase.sentences()),
                        frame,
                        probe.calls(termsCase.target()));
        final StageProbe.Counts counts = probe.since(mark);
        final String id = termsCase.id() + ":choice";
        if (chosen.isErr()) {
            return StageRow.failed(id, Objects.requireNonNull(chosen.error()).code(), counts);
        }
        final Choice choice = Objects.requireNonNull(chosen.data(), "choice");
        final List<String> misses = termsCase.terms().stream()
                .filter(verdict -> choice.kept().contains(LexiconEntry.keyOf(verdict.term())) != verdict.keep())
                .map(verdict -> verdict.term() + (verdict.keep() ? " dropped" : " kept"))
                .toList();
        return row(
                id,
                misses,
                "kept " + choice.kept().size(),
                counts,
                choice.undecided().size());
    }

    private StageRow review(final TermsCase termsCase) {
        final GlossaryRepository glossary =
                Guice.createInjector(new PersistenceModule()).getInstance(GlossaryRepository.class);
        termsCase.glossary().forEach(held -> glossary.add(entry(held)));
        final int mark = probe.mark();
        final Result<GlossaryReviewReport> reviewed = new TermReview(
                        TEMPLATES, MAPPER, glossary, new SuggestTargets(TEMPLATES, MAPPER))
                .review(
                        PROJECT,
                        StageFrames.segments(termsCase.sentences()),
                        frameOf(termsCase),
                        StageFrames.brief(termsCase.source(), termsCase.target())
                                .names(),
                        probe.calls(termsCase.target()));
        final StageProbe.Counts counts = probe.since(mark);
        final String id = termsCase.id() + ":review";
        if (reviewed.isErr()) {
            return StageRow.failed(id, Objects.requireNonNull(reviewed.error()).code(), counts);
        }
        final GlossaryReviewReport report = Objects.requireNonNull(reviewed.data(), "report");
        final Set<String> left = report.entries().stream()
                .map(entry -> entry.term().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        final List<String> misses = termsCase.glossary().stream()
                .filter(held -> left.contains(held.term().toLowerCase(Locale.ROOT)) != held.keep())
                .map(held -> held.term() + (held.keep() ? " removed" : " left"))
                .toList();
        return row(id, misses, "removed " + report.removed(), counts, 0);
    }

    private static CallFrame frameOf(final TermsCase termsCase) {
        return StageFrames.frame(
                StageFrames.brief(termsCase.source(), termsCase.target()), termsCase.source(), termsCase.target());
    }

    private static GlossaryEntry entry(final HeldTerm held) {
        return new GlossaryEntry(
                GlossaryIds.of(PROJECT, held.term()), PROJECT, held.term(), null, held.type(), Gender.UNKNOWN, false);
    }

    private static StageRow row(
            final String id,
            final List<String> misses,
            final String ok,
            final StageProbe.Counts counts,
            final int refused) {
        return StageRow.of(id, misses.isEmpty(), misses.isEmpty() ? ok : String.join("; ", misses), counts, refused);
    }
}
