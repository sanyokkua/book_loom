package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A glossary name with no target gets the spelling the book used for it, as a suggestion. The model here never reports
 * anything, writes «Бартімей» for {@code Bartimaeus} three times and then drifts to «Бартимей» unless its prompt names
 * the spelling; each chapter is one chunk, so the third chapter's pairs are learned before the fourth is read.
 */
class NameSpellingJobTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
    private static final Pattern SHOWN = Pattern.compile("<Text>\\n(.*?)\\n</Text>", Pattern.DOTALL);
    private static final String SPELLING_LINE = "Bartimaeus → Бартімей";
    private static final String P4 = "Bartimaeus sat in the dark.";
    private static final List<List<String>> CHAPTERS = List.of(
            List.of("Bartimaeus walked to the harbour.", "The boats rested in the harbour."),
            List.of("Bartimaeus opened the old door.", "The lamp burned all night."),
            List.of("Bartimaeus smiled at the sea.", "The wind rose over the hills."),
            List.of(P4, "The bell rang twice."));
    private static final Map<String, String> TARGETS = Map.of(
            "Bartimaeus walked to the harbour.",
            "%s пішов до гавані.",
            "The boats rested in the harbour.",
            "Човни спокійно стояли біля причалу.",
            "Bartimaeus opened the old door.",
            "%s відчинив старі двері.",
            "The lamp burned all night.",
            "Лампа горіла цілу ніч.",
            "Bartimaeus smiled at the sea.",
            "%s посміхнувся морю.",
            "The wind rose over the hills.",
            "Вітер здійнявся над пагорбами.",
            P4,
            "%s сів у темряві.",
            "The bell rang twice.",
            "Двічі продзвенів дзвін.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** Never reports terms; drifts to «Бартимей» in the fourth chapter unless its prompt names «Бартімей». */
    private static final class DriftingModel implements ChatModel {

        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final String user = userMessage(request);
            final String name = user.contains(P4) && !user.contains(SPELLING_LINE) ? "Бартимей" : "Бартімей";
            final Matcher items = ITEM.matcher(user);
            final List<String> entries = new ArrayList<>();
            while (items.find()) {
                entries.add("{\"id\":\"" + items.group(1) + "\",\"target\":\"" + target(items.group(2), name) + "\"}");
            }
            final String reply = !entries.isEmpty()
                    ? "{\"items\":[" + String.join(",", entries) + "]}"
                    : TranslationJobTestSupport.targetReply(target(shown(user), name));
            return Result.ok(new ChatResponse(reply, FinishReason.STOP));
        }

        private static String shown(final String user) {
            final Matcher matcher = SHOWN.matcher(user);
            return matcher.find() ? matcher.group(1) : "";
        }

        private static String target(final String source, final String name) {
            return Objects.requireNonNull(TARGETS.get(source)).formatted(name);
        }
    }

    private TestProject book(final boolean locked, @Nullable final String target) {
        final TestProject project =
                project(TestBooks.epub(tempDir.resolve("Book.epub"), CHAPTERS, "en"), brief("en", "uk"));
        project.stores()
                .glossary()
                .add(new GlossaryEntry(
                        "g1", project.id(), "Bartimaeus", target, TermType.CHARACTER, Gender.MALE, locked));
        return project;
    }

    private static GlossaryEntry bartimaeus(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().glossary().all(project.id()).data())
                .getFirst();
    }

    private static List<String> storedTargets(final TestProject project) {
        return Objects.requireNonNull(
                        project.stores().segments().all(project.id()).data())
                .stream()
                .map(SegmentRecord::machineTarget)
                .filter(Objects::nonNull)
                .toList();
    }

    @Test
    void run_nameWithNoTarget_theFourthChapterIsShownTheSpellingTheBookUsed() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book(false, null);

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED))
                .run());

        assertThat(bartimaeus(project).target()).isEqualTo("Бартімей");
        assertThat(bartimaeus(project).isSuggested()).isTrue();
        assertThat(storedTargets(project)).noneMatch(target -> target.contains("Бартимей"));
    }

    @Test
    void run_nameWithTheMansOwnTarget_isNeverReplacedByWhatTheBookUsed() {
        final DriftingModel model = new DriftingModel();
        final TestProject project = book(false, "Бартимей");

        report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED))
                .run());

        assertThat(bartimaeus(project).target()).isEqualTo("Бартимей");
        assertThat(bartimaeus(project).isSuggested()).isFalse();
    }
}
