package ua.bookloom.pipeline;

import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A Markdown book of chapters, each a {@code #} heading and three long one-sentence paragraphs (four segments, over the
 * 300-token chapter minimum), and the scripted model that drafts it and summarises its chapters.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SummaryBooks {

    /** The segments of one chapter: its heading and three paragraphs. */
    static final int CHAPTER_SEGMENTS = 4;

    static final String SUMMARY_FORMAT = "chapter_summary";
    static final String EARLIER = "[Earlier chapters]";

    private static final int PARAGRAPHS = 3;
    private static final int SPARE_REPLIES = 64;
    private static final int SPARE_SUMMARIES = 8;
    private static final String DRAFT = "Човни чекали біля берега, поки річка повільно текла повз старий млин.";
    private static final String SUMMARY = "Човни чекали біля берега річки.";
    private static final String TAIL = " the boats waited by the shore while the river ran slowly past the old mill,"
            + " the narrow bridge, the quiet market and the long grey wall of the town, and the people who lived there"
            + " watched the water rise a little with every hour of the long and cloudy afternoon, saying nothing at"
            + " all about the rain that was coming down from the hills or about the ferry that had not crossed since";
    private static final List<String> NUMBERS = List.of("One", "Two", "Three", "Four");

    /** Writes a book of {@code chapters} chapters (at most four) and imports it with the given switch. */
    static TestProject book(final Path dir, final int chapters, final boolean summaries) {
        final String content = IntStream.rangeClosed(1, chapters)
                .mapToObj(SummaryBooks::chapter)
                .collect(Collectors.joining("\n\n"));
        return project(
                TestBooks.markdown(dir.resolve("Book.md"), content),
                brief("en", "uk").withChapterSummaries(summaries));
    }

    /** The heading of chapter {@code number}, as its segment holds it. */
    static String heading(final int number) {
        return "Chapter " + NUMBERS.get(number - 1);
    }

    /** A phrase only chapter {@code number}'s paragraphs hold. */
    static String marker(final int number) {
        return "In the " + NUMBERS.get(number - 1).toLowerCase(java.util.Locale.ROOT) + " part";
    }

    private static String chapter(final int number) {
        final String paragraph = marker(number) + TAIL + " the morning.";
        return "# " + heading(number) + "\n\n"
                + IntStream.range(0, PARAGRAPHS).mapToObj(index -> paragraph).collect(Collectors.joining("\n\n"));
    }

    /** A model with a draft for every segment and a summary for every chapter. */
    static ScriptedChatModel model() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                IntStream.range(0, SPARE_REPLIES).mapToObj(index -> DRAFT).toArray(String[]::new));
        IntStream.range(0, SPARE_SUMMARIES).forEach(index -> model.answerTo(SUMMARY_FORMAT, summaryReply()));
        return model;
    }

    static Result<ChatResponse> summaryReply() {
        return Result.ok(new ChatResponse("{\"summary\":\"" + SUMMARY + "\"}", FinishReason.STOP));
    }

    /** Runs a job that is stopped once {@code decisions} segments are decided; returns its events. */
    static List<JobEvent> stoppedAfter(final TestProject project, final ScriptedChatModel model, final int decisions) {
        final TranslationJobImpl job = TranslationJobTestSupport.job(project, model);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        job.subscribe(event -> {
            events.add(event);
            if (event instanceof SegmentDecided decided
                    && decided.progress().accepted() + decided.progress().flagged() == decisions) {
                job.cancel();
            }
        });
        TranslationJobTestSupport.report(job.run());
        return events;
    }

    /** Stores the brief with the chapter-summary switch set, as the Book Brief screen does between runs. */
    static void switchSummaries(final TestProject project, final boolean on) {
        final Project stored = Objects.requireNonNull(
                        project.stores().projects().find(project.id()).data(), "project")
                .orElseThrow();
        project.stores().projects().save(stored.withBrief(stored.brief().withChapterSummaries(on)));
    }

    static List<ChapterSummary> stored(final TestProject project) {
        return Objects.requireNonNull(
                project.stores().summaries().list(project.id()).data(), "summaries");
    }

    static Optional<ChapterSummary> storedFor(final TestProject project, final String chapterKey) {
        return stored(project).stream()
                .filter(summary -> summary.chapterKey().equals(chapterKey))
                .findFirst();
    }

    static boolean isSummary(final ChatRequest request) {
        return request.callKind() == CallKind.CHAPTER_SUMMARY;
    }

    static String userText(final ChatRequest request) {
        return request.messages().getLast().content();
    }

    /** The requests sent before the first draft. */
    static List<ChatRequest> beforeFirstDraft(final List<ChatRequest> requests) {
        return requests.stream()
                .takeWhile(request -> request.callKind() != CallKind.DRAFT)
                .toList();
    }

    /** The first draft request whose items hold chapter {@code number}'s heading. */
    static ChatRequest firstDraftOf(final List<ChatRequest> requests, final int number) {
        final String item = "\">" + heading(number) + "</s>";
        return requests.stream()
                .filter(request -> request.callKind() == CallKind.DRAFT)
                .filter(request -> userText(request).contains(item))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no draft of chapter " + number));
    }
}
