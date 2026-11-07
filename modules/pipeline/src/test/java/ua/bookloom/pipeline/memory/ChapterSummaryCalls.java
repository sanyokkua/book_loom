package ua.bookloom.pipeline.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.project.ChapterBoundaries.Chapter;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;

/** An invented ru→uk chapter and the chapter-summary call over a scripted model, for the call's and its log's tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChapterSummaryCalls {

    static final String FORMAT = "chapter_summary";
    static final BookBrief BRIEF = BookBrief.defaults("ru").withLanguages("ru", "uk");
    static final CallFrame FRAME = new CallFrame("ru", "uk", StyleSheet.from(BRIEF), ForeignPassagePolicy.KEEP);
    static final LexiconFilter FILTER = LexiconFilter.of("ru", "uk");
    static final List<String> SOURCE =
            List.of("Мирта вышла из мельницы и пошла к городу.", "В городе она нашла брата у старого моста.");

    /** The decided translation of {@link #SOURCE}, whose words the summary's sentences are checked against. */
    static final List<String> TARGET = List.of(
            "Мірта вийшла з млина і пішла до міста. Мірта мовчить.",
            "У місті вона знайшла брата біля старого мосту.",
            "Дорога займає менше години.");

    /** A glossary entry no line of {@link #SOURCE} names. */
    static final GlossaryEntry GLAFIRA =
            GlossaryEntry.of("p:Глафира", "p", "Глафира", "Глафіра", TermType.CHARACTER, Gender.FEMALE, false);

    static Chapter chapter(final int number, final int segments) {
        final List<String> ids = IntStream.range(0, segments)
                .mapToObj(index -> "ch" + number + ".xhtml:" + index)
                .toList();
        return new Chapter(
                "ch" + number + ".xhtml:0", number, "ch" + number + ".xhtml", 0, segments, ids, 50, "hash", false);
    }

    static BookMap map(final List<GlossaryEntry> entries) {
        return BookMap.of(entries, List.of(), BRIEF);
    }

    static Result<ChatResponse> reply(final String summary) {
        return Result.ok(new ChatResponse("{\"summary\":\"" + summary + "\"}", FinishReason.STOP));
    }

    static ChapterSummaryCall call(final ScriptedChatModel model, final Consumer<JobEvent> events) {
        final JobModelCalls calls = new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                events,
                Clock.systemUTC(),
                "uk",
                ModelWindow.DEFAULT_WINDOW);
        return new ChapterSummaryCall(
                new ContextBuilder(new PromptTemplates()), new ObjectMapper(), FRAME, calls, ModelProfile.Tier.SMALL);
    }
}
