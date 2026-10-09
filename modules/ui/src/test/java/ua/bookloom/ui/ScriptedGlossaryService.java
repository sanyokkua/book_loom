package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * A hand-written {@link GlossaryService} that records each call and answers with what a test queued, in order; a call
 * with nothing queued answers an {@code internal} error so an unexpected call is loud.
 */
public final class ScriptedGlossaryService implements GlossaryService {

    private static final long HOLD_SECONDS = 10;

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<GlossaryEntry> added = new CopyOnWriteArrayList<>();
    private final List<GlossaryEntry> updated = new CopyOnWriteArrayList<>();
    private final Queue<Result<?>> answers = new ConcurrentLinkedQueue<>();
    private final Queue<JobEvent> events = new ConcurrentLinkedQueue<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> unknownGenderCalls = new CopyOnWriteArrayList<>();
    private volatile List<UnknownGender> unknownGenders = List.of();
    private volatile boolean holding;

    /** Queues the answer the next call gets, whatever the method; the caller states the matching result type. */
    public void willAnswer(final Result<?> answer) {
        answers.add(answer);
    }

    /** Every call as {@code method(arguments)}, in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    /** Every entry {@code add} was asked to store, in order. */
    public List<GlossaryEntry> added() {
        return List.copyOf(added);
    }

    /** Every entry {@code update} was asked to store, in order. */
    public List<GlossaryEntry> updated() {
        return List.copyOf(updated);
    }

    @Override
    public Result<List<GlossaryEntry>> entries(final String projectId) {
        return answer("entries(" + projectId + ")");
    }

    /** Sets the characters {@code unknownGenders} answers with; none until a test says so. */
    public void willListUnknownGenders(final List<UnknownGender> characters) {
        unknownGenders = List.copyOf(characters);
    }

    /** Every {@code unknownGenders} call as {@code projectId:minMentions}, kept apart from {@link #calls()}. */
    public List<String> unknownGenderCalls() {
        return List.copyOf(unknownGenderCalls);
    }

    @Override
    public Result<List<UnknownGender>> unknownGenders(final String projectId, final int minMentions) {
        unknownGenderCalls.add(projectId + ":" + minMentions);
        return Result.ok(unknownGenders);
    }

    @Override
    public Result<List<GlossaryEntry>> scan(final String projectId) {
        return answer("scan(" + projectId + ")");
    }

    /**
     * Makes every later model scan and review wait, as a slow model would, until {@link #releaseModelCalls()}; the
     * wait ends by itself after a few seconds so a test that forgets the release cannot hang.
     */
    public void holdModelCalls() {
        holding = true;
    }

    /** Lets every held model scan and review answer. */
    public void releaseModelCalls() {
        holding = false;
        release.countDown();
    }

    private void waitIfHeld() {
        if (!holding) {
            return;
        }
        try {
            release.await(HOLD_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Queues an event the next model scan or review reports to its progress receiver before it answers. */
    public void willReport(final JobEvent event) {
        events.add(event);
    }

    @Override
    public Result<List<GlossaryEntry>> prescan(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        report(progress);
        waitIfHeld();
        return answer("prescan(" + projectId + ")");
    }

    @Override
    public Result<GlossaryReviewReport> review(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        report(progress);
        waitIfHeld();
        return answer("review(" + projectId + ")");
    }

    private void report(final Consumer<JobEvent> progress) {
        JobEvent event = events.poll();
        while (event != null) {
            progress.accept(event);
            event = events.poll();
        }
    }

    @Override
    public Result<GlossaryEntry> add(final GlossaryEntry entry) {
        added.add(entry);
        return answer("add(" + entry.term() + ")");
    }

    @Override
    public Result<GlossaryEntry> update(final GlossaryEntry entry) {
        updated.add(entry);
        return answer("update(" + entry.term() + ")");
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String entryId) {
        return answer("remove(" + projectId + ", " + entryId + ")");
    }

    @Override
    public Result<GlossaryImportReport> importCsv(final String projectId, final Path source) {
        return answer("importCsv(" + projectId + ", " + source + ")");
    }

    @Override
    public Result<Path> exportCsv(final String projectId, final Path destination) {
        return answer("exportCsv(" + projectId + ", " + destination + ")");
    }

    @SuppressWarnings("unchecked")
    private <T> Result<T> answer(final String call) {
        calls.add(call);
        final Result<?> queued = answers.poll();
        if (queued != null) {
            return (Result<T>) queued;
        }
        return Result.err(
                AppError.of(ErrorCode.internal, "Not scripted", "The scripted glossary service has no answer."));
    }
}
