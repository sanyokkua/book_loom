package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.context.AcceptedTargets;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.BookRules;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.RunFrame;
import ua.bookloom.pipeline.context.RunFrames;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.project.ChapterBoundaries;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The book an eval case stands for: one body unit of the case's earlier pairs, its chunk and the segments after it,
 * the earlier pairs accepted with their targets, and the planner a run on it would start with.
 *
 * @param chunk the chunk's segments, in order
 * @param accepted the earlier segments' kept targets
 * @param planner the planner bound to the frame a run on the book starts with
 */
record EvalBook(List<Segment> chunk, AcceptedTargets accepted, TranslatePlanner planner) {

    private static final String PROJECT = "eval";
    private static final String UNIT = "eval";

    static EvalBook of(
            final EvalProject.Setup setup,
            final BookBrief brief,
            final GlossaryRepository glossary,
            final LexiconRepository lexicon,
            final int window,
            final PromptTemplates templates) {
        final List<EvalContext.Pair> preceding = setup.context().preceding();
        final List<Segment> before = EvalProject.segments(
                preceding.stream().map(EvalContext.Pair::source).toList(), 0);
        final List<Segment> chunk = EvalProject.segments(setup.chunk(), before.size());
        final List<Segment> unit = new ArrayList<>(before);
        unit.addAll(chunk);
        unit.addAll(EvalProject.segments(setup.after(), before.size() + chunk.size()));
        final Map<String, String> targets = new LinkedHashMap<>();
        // A pair with no target stands for a segment drafted but not yet decided, which a window shows as source only.
        IntStream.range(0, before.size())
                .filter(i -> !preceding.get(i).target().isBlank())
                .forEach(i -> targets.put(before.get(i).id(), preceding.get(i).target()));
        final Document document = new Document(
                PROJECT,
                BookFormat.TXT,
                setup.bookLanguage(),
                null,
                null,
                null,
                "hash",
                Map.of(),
                List.of(new Unit(UNIT, 0, UNIT + ".txt", "text/plain", new SkeletonHandle(UNIT), unit, UnitRole.BODY)));
        return new EvalBook(
                chunk,
                id -> Result.ok(Optional.ofNullable(targets.get(id))),
                planner(brief, document, glossary, lexicon, window, templates));
    }

    // The run's frame is built at Start from the brief, the book and the map as it stands, as a run builds it.
    private static TranslatePlanner planner(
            final BookBrief brief,
            final Document document,
            final GlossaryRepository glossary,
            final LexiconRepository lexicon,
            final int window,
            final PromptTemplates templates) {
        final List<GlossaryEntry> entries =
                Objects.requireNonNull(glossary.all(PROJECT).data(), "glossary");
        final List<LexiconEntry> terms =
                Objects.requireNonNull(lexicon.all(PROJECT).data(), "lexicon");
        final ModelProfile profile = ModelProfile.defaults();
        final RunFrame frame = Objects.requireNonNull(
                new RunFrames(templates, new BookRules(templates.languageRules()))
                        .build(
                                brief,
                                document,
                                BookIndex.of(document),
                                ChapterBoundaries.of(
                                        document, IndexTestBooks.navigation(document), brief.sourceLanguage()),
                                BookMap.of(entries, terms, brief, document.units()),
                                profile)
                        .data(),
                "run frame");
        return new TranslatePlanner(
                new ContextBuilder(templates),
                frame,
                profile,
                () -> window,
                document.units(),
                () -> Result.ok(List.of()),
                brief::summariesOn);
    }
}
