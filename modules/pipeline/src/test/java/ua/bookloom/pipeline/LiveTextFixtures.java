package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * The book of the specification's live-text examples: eleven body chapters {@code ch01.xhtml}…{@code ch11.xhtml}, of
 * which the seventh holds the door sentence as its 42nd paragraph, {@code ch07.xhtml:41}. Every segment but the ones a
 * test names is stored as already accepted, so a run starts exactly where the test looks.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LiveTextFixtures {

    static final String DOOR_ID = "ch07.xhtml:41";
    static final String BEFORE_DOOR_ID = "ch07.xhtml:40";
    static final String DOOR_SOURCE = "He opened the <em>old</em> door.";
    static final String DOOR_MASKED_TARGET = "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.";
    static final String DOOR_REVIEWED = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"}]}";
    static final int CHAPTERS = 11;
    static final int DOOR_CHAPTER = 7;

    /** The eleven-chapter book with the door sentence at {@code ch07.xhtml:41}, on Balanced, all but :40 and :41 decided. */
    static TestProject doorProject(final Path directory) {
        final List<String> seventh = new ArrayList<>(
                IntStream.range(0, 40).mapToObj(index -> "Line " + index + ".").toList());
        seventh.add(ChunkRunFixtures.S0);
        seventh.add(DOOR_SOURCE);
        return decidedExcept(chapters(directory, seventh), Set.of(BEFORE_DOOR_ID, DOOR_ID));
    }

    /** The draft of :40, the door's draft and the chunk's reviewer verdict of ok. */
    static ScriptedChatModel doorModel() {
        return TranslationJobTestSupport.replies(ChunkRunFixtures.T0, DOOR_MASKED_TARGET)
                .answerTo(ChunkRunFixtures.REVIEW, Result.ok(new ChatResponse(DOOR_REVIEWED, FinishReason.STOP)));
    }

    /**
     * The eleven-chapter book whose seventh chapter holds {@code paragraphs}, on Balanced, with every segment outside
     * the seventh chapter already decided.
     */
    static TestProject seventhChapterPending(final Path directory, final List<String> paragraphs) {
        final TestProject project = chapters(directory, paragraphs);
        final Set<String> pending = IntStream.range(0, paragraphs.size())
                .mapToObj(index -> "ch07.xhtml:" + index)
                .collect(Collectors.toSet());
        return decidedExcept(project, pending);
    }

    /** Answers every reviewer call with ok and every other call with a Cyrillic target. */
    static ChatModel acceptingModel() {
        return (final ChatRequest request) -> {
            final String format =
                    Objects.requireNonNull(request.responseFormat(), "format").name();
            final String content = ChunkRunFixtures.REVIEW.equals(format)
                    ? DOOR_REVIEWED
                    : TranslationJobTestSupport.targetReply("Рядок номер.");
            return Result.ok(new ChatResponse(content, FinishReason.STOP));
        };
    }

    private static TestProject chapters(final Path directory, final List<String> seventh) {
        final List<String> names = IntStream.rangeClosed(1, CHAPTERS)
                .mapToObj(number -> String.format("ch%02d.xhtml", number))
                .toList();
        final List<List<String>> paragraphs = IntStream.rangeClosed(1, CHAPTERS)
                .mapToObj(number -> number == DOOR_CHAPTER ? seventh : List.of("Chapter " + number + "."))
                .toList();
        final Path book = TestBooks.epubAtRoot(directory.resolve("Book.epub"), names, paragraphs);
        return TranslationJobTestSupport.project(
                book, TranslationJobTestSupport.brief("en", "uk", QualityDial.BALANCED));
    }

    private static TestProject decidedExcept(final TestProject project, final Set<String> pending) {
        final List<SegmentRecord> records =
                Objects.requireNonNull(
                                project.stores().segments().all(project.id()).data(), "records")
                        .stream()
                        .map(record -> pending.contains(record.segmentId())
                                ? record
                                : record.withStatus(SegmentStatus.ACCEPTED))
                        .toList();
        project.stores().segments().saveAll(project.id(), records);
        return project;
    }
}
