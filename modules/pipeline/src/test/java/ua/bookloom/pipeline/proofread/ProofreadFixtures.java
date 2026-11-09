package ua.bookloom.pipeline.proofread;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.BookRules;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.RunFrame;
import ua.bookloom.pipeline.context.RunFrames;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.project.ChapterBoundaries;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;

/** An invented book of one chapter unit, its stored records and a planner over it, for the proofread tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProofreadFixtures {

    static final String PROJECT = "p";
    static final PromptTemplates TEMPLATES = new PromptTemplates();

    /** A chapter unit whose segment at position {@code n} holds {@code lines[n]}; ids are {@code unit:n}. */
    static Document chapter(final String unit, final String... lines) {
        return IndexTestBooks.document(IndexTestBooks.unit(unit, UnitRole.BODY, lines));
    }

    /** Lines 0 to {@code count - 1}, filler but for the given ones, so a test can name a segment by its position. */
    static String[] lines(final int count, final String... positionAndText) {
        final List<String> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            lines.add("Строка " + index + " идёт своим чередом.");
        }
        for (int at = 0; at + 1 < positionAndText.length; at += 2) {
            lines.set(Integer.parseInt(positionAndText[at]), positionAndText[at + 1]);
        }
        return lines.toArray(String[]::new);
    }

    static BookBrief brief(final String source, final String target) {
        return BookBrief.defaults(source).withLanguages(source, target);
    }

    static CallFrame frame(final BookBrief brief) {
        return new CallFrame(
                Objects.requireNonNull(brief.sourceLanguage()),
                Objects.requireNonNull(brief.targetLanguage()),
                StyleSheet.from(brief),
                ForeignPassagePolicy.KEEP);
    }

    static RunFrame runFrame(final Document document, final BookBrief brief, final ChatModel model) {
        return Objects.requireNonNull(new RunFrames(TEMPLATES, new BookRules(LanguageRules.bundled()))
                .build(
                        brief,
                        document,
                        BookIndex.of(document),
                        ChapterBoundaries.of(document, IndexTestBooks.navigation(document), brief.sourceLanguage()),
                        BookMap.of(List.of(), List.of(), brief),
                        model.profile())
                .data());
    }

    static TranslatePlanner planner(
            final PromptTemplates templates, final Document document, final BookBrief brief, final ChatModel model) {
        return new TranslatePlanner(
                new ContextBuilder(templates),
                runFrame(document, brief, model),
                model.profile(),
                () -> ModelWindow.DEFAULT_WINDOW,
                document.units(),
                () -> Result.ok(List.of()),
                () -> false);
    }

    static JobModelCalls calls(final ChatModel model, final Consumer<JobEvent> events) {
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                events,
                Clock.systemUTC(),
                "uk",
                ModelWindow.DEFAULT_WINDOW);
    }

    static BookMap map(final List<GlossaryEntry> entries, final BookBrief brief) {
        return BookMap.of(entries, List.of(), brief);
    }

    /** A decided record of {@code segmentId} holding {@code text}. */
    static SegmentRecord decided(final String segmentId, final SegmentStatus status, final String text) {
        final String unit = segmentId.substring(0, segmentId.lastIndexOf(':'));
        final int ord = Integer.parseInt(segmentId.substring(segmentId.lastIndexOf(':') + 1));
        return new SegmentRecord(
                PROJECT,
                segmentId,
                unit,
                ord,
                ua.bookloom.api.document.SegmentKind.PARAGRAPH,
                status,
                text,
                text,
                null,
                null,
                1.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                false,
                null);
    }
}
