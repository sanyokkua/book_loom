package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.eval.StageCases.Expect;
import ua.bookloom.pipeline.eval.StageCases.PreScanCase;
import ua.bookloom.pipeline.glossary.PreScan;
import ua.bookloom.pipeline.glossary.SuggestTargets;
import ua.bookloom.pipeline.glossary.TermReview;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Sends each case's lines through the production {@link PreScan} (proposal, verdict and suggestion calls) over an empty
 * in-memory glossary and compares the entries it kept with what the case expects: a name with its type and gender, and
 * the common-word traps left out.
 */
@Slf4j
final class PreScanEvalRunner {

    private static final String PROJECT = "eval";
    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StageProbe probe;

    PreScanEvalRunner(final ChatModel model) {
        this.probe = new StageProbe(Objects.requireNonNull(model, "model"));
    }

    StageProbe probe() {
        return probe;
    }

    List<StageRow> run(final List<PreScanCase> cases) {
        return cases.stream().map(this::one).toList();
    }

    private StageRow one(final PreScanCase scanCase) {
        final GlossaryRepository glossary =
                Guice.createInjector(new PersistenceModule()).getInstance(GlossaryRepository.class);
        final SuggestTargets suggest = new SuggestTargets(TEMPLATES, MAPPER);
        final PreScan scan =
                new PreScan(TEMPLATES, MAPPER, glossary, new TermReview(TEMPLATES, MAPPER, glossary, suggest), suggest);
        final BookBrief brief = StageFrames.brief(scanCase.source(), scanCase.target());
        final CallFrame frame = StageFrames.frame(brief, scanCase.source(), scanCase.target());
        final int mark = probe.mark();
        log.info("Pre-scan eval case={}", scanCase.id());
        final Result<EntryChanges<GlossaryEntry>> kept = scan.scan(
                PROJECT,
                StageFrames.segments(scanCase.sentences()),
                frame,
                brief.names(),
                probe.calls(scanCase.target()));
        final StageProbe.Counts counts = probe.since(mark);
        if (kept.isErr()) {
            final AppError error = Objects.requireNonNull(kept.error(), "error");
            return new StageRow(
                    scanCase.id(),
                    false,
                    "error " + error.code(),
                    counts.calls(),
                    counts.failed(),
                    counts.repeated(),
                    0);
        }
        return score(scanCase, Objects.requireNonNull(kept.data(), "kept").added(), counts);
    }

    private static StageRow score(
            final PreScanCase scanCase, final List<GlossaryEntry> kept, final StageProbe.Counts counts) {
        final List<String> misses = new ArrayList<>();
        for (final Expect expect : scanCase.expect()) {
            final GlossaryEntry entry = kept.stream()
                    .filter(candidate -> candidate.term().equalsIgnoreCase(expect.term()))
                    .findFirst()
                    .orElse(null);
            final String miss = miss(expect, entry);
            if (miss != null) {
                misses.add(expect.term() + ": " + miss);
            }
        }
        final String detail = misses.isEmpty() ? "kept " + kept.size() : String.join("; ", misses);
        return new StageRow(
                scanCase.id(), misses.isEmpty(), detail, counts.calls(), counts.failed(), counts.repeated(), 0);
    }

    private static String miss(final Expect expect, final GlossaryEntry entry) {
        if (!expect.keep()) {
            return entry == null ? null : "kept but is not a name";
        }
        if (entry == null) {
            return "not kept";
        }
        if (expect.type() != null && entry.type() != expect.type()) {
            return "type " + entry.type();
        }
        return expect.gender() != null && entry.gender() != expect.gender() ? "gender " + entry.gender() : null;
    }
}
