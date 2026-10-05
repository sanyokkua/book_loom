package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.audit.AuditFindings;
import ua.bookloom.pipeline.audit.AuditRecorder;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.review.ReviewCounting;
import ua.bookloom.pipeline.review.ReviewQueries;
import ua.bookloom.pipeline.run.RunAudit;
import ua.bookloom.pipeline.run.RunStores;

/**
 * A finished scripted run over a four-paragraph book: the audit lists an English leftover, a stray quote and a dropped
 * glossary name that a target now holds, names the check that fired for each, and says nothing about the clean one.
 */
class FinalAuditJobTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
    private static final String HARBOUR = "The harbour was quiet at dawn.";
    private static final String DOOR = "The old wooden door opened slowly and the captain looked into the dark room.";
    private static final String QUOTE = "He said \"the boats would return before the storm\" and went home.";
    private static final String NELL = "Nell counted every crate on the quay twice that morning.";
    private static final Map<String, String> TARGETS = Map.of(
            HARBOUR, "Гавань на світанку була тихою.",
            DOOR, "Старі дерев'яні двері повільно відчинилися, і капітан зазирнув до темної кімнати.",
            QUOTE, "Він сказав: «човни повернуться до початку бурі» і пішов додому.",
            NELL, "Нелл двічі перерахувала кожен ящик на причалі того ранку.");
    private static final String ENGLISH_LEFTOVER =
            "The old wooden door opened slowly and the captain looked into the dark room again.";
    private static final String STRAY_QUOTE = "Він сказав: «човни повернуться до початку бурі і пішов додому.";
    private static final String NAME_DROPPED = "Вона двічі перерахувала кожен ящик на причалі того ранку.";
    private static final String DOUBLED = "Гавань на на світанку була тихою.";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** Answers each item of a batch with its scripted target, replacing the ones a test wants damaged. */
    private static final class ScriptedModel implements ChatModel {

        private final Map<String, String> replacements;
        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        ScriptedModel(final Map<String, String> replacements) {
            this.replacements = replacements;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final Matcher items = ITEM.matcher(userMessage(request));
            final StringBuilder reply = new StringBuilder("{\"items\":[");
            while (items.find()) {
                final String source = items.group(2);
                final String target = replacements.getOrDefault(source, Objects.requireNonNull(TARGETS.get(source)));
                reply.append(reply.charAt(reply.length() - 1) == '[' ? "" : ",")
                        .append("{\"id\":\"")
                        .append(items.group(1))
                        .append("\",\"target\":\"")
                        .append(target)
                        .append("\"}");
            }
            return Result.ok(new ChatResponse(reply.append("]}").toString(), FinishReason.STOP));
        }
    }

    private TestProject finished(final Map<String, String> replacements) {
        final TestProject book = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", HARBOUR, DOOR, QUOTE, NELL)),
                brief("en", "uk"));
        book.stores()
                .glossary()
                .add(new GlossaryEntry("g1", book.id(), "Nell", "Нелл", TermType.CHARACTER, Gender.UNKNOWN, false));
        report(batchedJob(book, new ScriptedModel(replacements), new RunRequest(book.id(), ReviewMode.UNATTENDED))
                .run());
        return book;
    }

    private static void damage(final TestProject book, final String segmentId, final String text) {
        book.stores().segments().update(book.id(), segmentId, record -> record.withMachineTarget(text, text));
    }

    private static List<SuspiciousSegment> audited(final TestProject book) {
        final RunStores stores = book.stores();
        return Objects.requireNonNull(new AuditRecorder(
                        stores.projects(),
                        stores.segments(),
                        stores.glossary(),
                        stores.openProjects(),
                        WordValidator.none())
                .run(book.id())
                .data());
    }

    private static List<SegmentRecord> stored(final TestProject book) {
        return Objects.requireNonNull(book.stores().segments().all(book.id()).data());
    }

    @Test
    void run_cleanBook_listsNothingSuspicious() {
        final TestProject run = finished(Map.of());

        assertThat(audited(run)).isEmpty();
    }

    @Test
    void audit_leftoverStrayQuoteAndDroppedName_areNamedWithTheCheckThatFired() {
        final TestProject run = finished(Map.of());
        damage(run, "Book.md:1", ENGLISH_LEFTOVER);
        damage(run, "Book.md:2", STRAY_QUOTE);
        damage(run, "Book.md:3", NAME_DROPPED);

        final List<SuspiciousSegment> suspicious = audited(run);

        assertThat(suspicious)
                .extracting(SuspiciousSegment::segmentId, SuspiciousSegment::checks)
                .containsExactly(
                        tuple("Book.md:1", List.of("language-identity")),
                        tuple("Book.md:2", List.of("quote-balance")),
                        tuple("Book.md:3", List.of("name-missing")));
    }

    @Test
    void audit_damagedBook_listsThemUnderTheSuspiciousFilterAndCountsThem() {
        final TestProject run = finished(Map.of());
        damage(run, "Book.md:1", ENGLISH_LEFTOVER);
        damage(run, "Book.md:3", NAME_DROPPED);
        audited(run);

        final List<SegmentView> listed = Objects.requireNonNull(new ReviewQueries(
                        run.stores().openProjects(),
                        run.stores().projects(),
                        run.stores().segments(),
                        run.deferrals())
                .queue(run.id(), ReviewFilter.SUSPICIOUS)
                .data());
        final ReviewCounts counts = ReviewCounting.count(stored(run), Set.of());

        assertThat(listed).extracting(SegmentView::segmentId).containsExactly("Book.md:1", "Book.md:3");
        assertThat(listed.getFirst().findings())
                .anyMatch(finding -> finding.raisedBy().equals("audit:language-identity"));
        assertThat(counts.suspicious()).isEqualTo(2);
    }

    @Test
    void audit_segmentFixedSinceTheLastAudit_losesItsMark() {
        final TestProject run = finished(Map.of());
        damage(run, "Book.md:1", ENGLISH_LEFTOVER);
        audited(run);
        damage(run, "Book.md:1", Objects.requireNonNull(TARGETS.get(DOOR)));

        final List<SuspiciousSegment> again = audited(run);

        assertThat(again).isEmpty();
        assertThat(storedFindings(run, "Book.md:1")).noneMatch(AuditFindings::isAudit);
    }

    @Test
    void audit_segmentAReviewerAccepted_isNotListedAgain() {
        final TestProject run = finished(Map.of());
        damage(run, "Book.md:1", ENGLISH_LEFTOVER);
        run.stores().segments().update(run.id(), "Book.md:1", record -> record.withReviewed(true));

        assertThat(audited(run)).isEmpty();
    }

    @Test
    void run_acceptedDraftWithADoubledWord_isAuditedWhenTheRunCompletes() {
        final TestProject run = finished(Map.of(HARBOUR, DOUBLED));

        assertThat(stored(run).stream().filter(AuditFindings::isSuspicious))
                .extracting(SegmentRecord::segmentId)
                .containsExactly("Book.md:0");
        assertThat(AuditFindings.checksOf(storedFindings(run, "Book.md:0"))).containsExactly("duplicate-word");
    }

    private static List<QaFinding> storedFindings(final TestProject run, final String segmentId) {
        return Objects.requireNonNull(
                        run.stores().segments().find(run.id(), segmentId).data())
                .orElseThrow()
                .findings();
    }

    @Test
    void after_checkThatThrows_leavesTheCompletedRunAndItsRecordsAlone() {
        final TestProject run = finished(Map.of());
        final List<SegmentRecord> before = stored(run);
        final WordValidator broken = (target, language) -> {
            throw new IllegalStateException("the dictionary is gone");
        };

        assertThatCode(() -> RunAudit.after(JobState.COMPLETED, run.stores(), broken, run.id()))
                .doesNotThrowAnyException();
        assertThat(stored(run)).isEqualTo(before);
    }
}
