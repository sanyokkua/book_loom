package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;

/**
 * The four-paragraph book and the full-margin Cyrillic replies the chunk tests share: every reply is Cyrillic with a
 * length ratio inside 0.81–1.69 of its source, so each passes every soft check at full margin, while the upper-cased
 * echo of a source of 20 or more code points fails the echo and script checks outright.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChunkRunFixtures {

    static final String S0 = "The old man walked to the harbour.";
    static final String S1 = "The lighthouse stood on the cliff.";
    static final String S2 = "The boats rested in the harbour.";
    static final String S3 = "The wind rose over the dark sea.";
    static final String T0 = "Старий чоловік пішов до гавані.";
    static final String T1 = "Маяк стояв на прибережній скелі.";
    static final String T2 = "Човни спокійно стояли в гавані.";
    static final String T3 = "Вітер здійнявся над темним морем.";
    static final String ECHO1 = "THE LIGHTHOUSE STOOD ON THE CLIFF.";
    static final String ECHO2 = "THE BOATS RESTED IN THE HARBOUR.";
    static final String DOOR = "He opened the old door.";
    static final String DOOR_ECHO = "HE OPENED THE OLD DOOR.";
    static final String DOOR_TARGET = "Він відчинив старі двері.";
    static final String EDIT = "Він рвучко відчинив двері.";
    static final String JUDGE = "judge";
    static final String DRAFT = "draft";
    static final String FIX = "directed-fix";

    static Path fourParagraphs(final Path directory) {
        return TestBooks.markdown(directory.resolve("Book.md"), String.join("\n\n", S0, S1, S2, S3));
    }

    static Path threeParagraphs(final Path directory) {
        return TestBooks.markdown(directory.resolve("Book.md"), String.join("\n\n", S0, S1, S2));
    }

    static Path door(final Path directory) {
        return TestBooks.txt(directory.resolve("Book.txt"), DOOR);
    }

    /** A judge verdict that accepts every pair of the chunk at a score above every review mode's threshold. */
    static Result<ChatResponse> judged() {
        return Result.ok(new ChatResponse("{\"score\":0.9,\"verdict\":\"accept\"}", FinishReason.STOP));
    }

    static Result<ChatResponse> target(final String text) {
        return Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(text), FinishReason.STOP));
    }

    static List<String> formats(final ScriptedChatModel model) {
        return model.requests().stream()
                .map(request -> Objects.requireNonNull(request.responseFormat(), "format")
                        .name())
                .toList();
    }

    static String userMessage(final ChatRequest request) {
        return request.messages().get(1).content();
    }

    static String preceding(final String... targets) {
        return "<PreviousTranslations>\n" + String.join("\n\n", targets) + "\n</PreviousTranslations>";
    }

    static String shown(final String source) {
        return "<Text>\n" + source + "\n</Text>";
    }

    static void pauseOn(final TranslationJobImpl translation, final String segmentId, final JobEvent event) {
        if (event instanceof SegmentDecided decided && segmentId.equals(decided.segmentId())) {
            translation.pause();
        }
    }

    static LinkedBlockingQueue<Paused> pauses(final TranslationJobImpl translation) {
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> TranslationJobTestSupport.capturePaused(pauses, event));
        return pauses;
    }

    /** The stage of every {@link StageStarted} the job announces, in order. */
    static List<JobStage> stages(final TranslationJobImpl translation) {
        final List<JobStage> stages = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> recordStage(stages, event));
        return stages;
    }

    private static void recordStage(final List<JobStage> stages, final JobEvent event) {
        if (event instanceof StageStarted started) {
            stages.add(started.stage());
        }
    }

    static SegmentStatus status(final TranslationJobTestSupport.TestProject project, final String segmentId) {
        return TranslationJobTestSupport.stored(project, segmentId).status();
    }
}
