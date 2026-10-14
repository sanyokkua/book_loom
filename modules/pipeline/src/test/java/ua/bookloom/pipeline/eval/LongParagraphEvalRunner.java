package ua.bookloom.pipeline.eval;

import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.chunk.LongParagraphs;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.eval.EvalProject.Setup;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * Drafts each long paragraph (15i.X3) on the real draft path, {@code SegmentTranslator.translateSplit} over an
 * {@link EvalProject}, so the switch decides whether it goes in one call or in sentence groups exactly as it does in a
 * run. The draft step only drafts, so what is kept is the restored draft, which is what a grader reads.
 */
@Slf4j
final class LongParagraphEvalRunner {

    private static final String SOURCE = "en";
    private static final String TARGET = "uk";
    private static final SentenceSplitter SPLITTER =
            Guice.createInjector(new DocumentModule()).getInstance(SentenceSplitter.class);

    private final ModelCalls calls;

    LongParagraphEvalRunner(final ModelCalls calls) {
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /** Drafts every paragraph, one after the other, as the single-flight gate would. */
    LongParagraphReport run(final List<LongParagraph> paragraphs) {
        Objects.requireNonNull(paragraphs, "paragraphs");
        final boolean groups = LongParagraphs.isOn();
        log.info("Long-paragraph eval paragraphs={} groups={}", paragraphs.size(), groups);
        return new LongParagraphReport(
                groups, paragraphs.stream().map(this::draft).toList());
    }

    private LongParagraphReport.Row draft(final LongParagraph paragraph) {
        final AtomicInteger counted = new AtomicInteger();
        final ModelCalls counting = (kind, segmentId, request) -> {
            counted.incrementAndGet();
            return calls.call(kind, segmentId, request);
        };
        final EvalProject project =
                EvalProject.of(Setup.single(SOURCE, TARGET, List.of(), EvalContext.none(), paragraph.text()), counting);
        final Result<DraftOutcome> result = project.translator()
                .translateSplit(
                        project.segment(0),
                        project.draftContext(0).draftContext(),
                        project.mask(0),
                        SPLITTER,
                        TokenBudget.MAX_CHUNK_TOKENS);
        final String target = result.data() instanceof DraftOutcome.Drafted drafted
                ? Objects.toString(drafted.restoredTarget(), "")
                : "";
        log.debug("Long paragraph {} calls={} drafted={}", paragraph.id(), counted.get(), !target.isEmpty());
        return new LongParagraphReport.Row(paragraph.id(), paragraph.words(), counted.get(), paragraph.text(), target);
    }
}
