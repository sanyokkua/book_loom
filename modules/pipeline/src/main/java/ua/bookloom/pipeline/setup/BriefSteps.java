package ua.bookloom.pipeline.setup;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SampleAnswer;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/**
 * The request steps of one brief suggestion for its progress dialog: what the model knows of the book, one per window
 * of the story, and the synthesis. The plan counts the calls the suggestion can make, so a step that sends none (a
 * skipped synthesis) ends with no call; a call the person stopped leaves its step open.
 */
@Slf4j
final class BriefSteps {

    private static final String KNOWLEDGE = "knowledge";
    private static final String SYNTHESIS = "synthesis";

    private final Consumer<JobEvent> sink;
    private final StepAnnouncer announcer;
    private int samples;

    BriefSteps(final Consumer<JobEvent> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.announcer = StepAnnouncer.over(sink);
    }

    void plan(final boolean knowledgeAsked, final int windows) {
        samples = windows;
        announcer.planned(StepKind.REQUEST, (knowledgeAsked ? 1 : 0) + windows + 1, false);
    }

    void knowledgeStarted() {
        announcer.started(StepKind.REQUEST, KNOWLEDGE, KNOWLEDGE, null);
    }

    void knowledgeEnded(final Result<Knowledge> result) {
        if (result.isErr()) {
            return;
        }
        final boolean failed = Objects.requireNonNull(result.data()).reason() == Reason.CALL_FAILED;
        announcer.ended(StepKind.REQUEST, KNOWLEDGE, failed ? StepResult.FAILED : StepResult.DONE);
    }

    void sampleStarted(final int number) {
        announcer.started(StepKind.REQUEST, label(number), label(number), null);
    }

    void sampleFailed(final int number, final AppError error) {
        if (error.code() != ErrorCode.cancelled) {
            announcer.ended(StepKind.REQUEST, label(number), StepResult.FAILED);
        }
    }

    /** Ends the window's step and shows what it answered; a reply that could not be read ends it failed. */
    void sampleRead(final int number, final @Nullable Map<BriefField, Answer> answers) {
        announcer.ended(StepKind.REQUEST, label(number), answers == null ? StepResult.FAILED : StepResult.DONE);
        final Map<BriefField, SampleAnswer> shown = new EnumMap<>(BriefField.class);
        if (answers != null) {
            answers.forEach((field, answer) -> shown.put(field, new SampleAnswer(answer.value(), answer.quote())));
        }
        log.trace("Brief window {} of {} answered {}", number, samples, shown);
        sink.accept(new BriefSampleRead(number, samples, shown));
    }

    void synthesisSkipped() {
        announcer.instant(StepKind.REQUEST, SYNTHESIS, SYNTHESIS, null, StepResult.NO_CALL);
    }

    void synthesisStarted() {
        announcer.started(StepKind.REQUEST, SYNTHESIS, SYNTHESIS, null);
    }

    void synthesisEnded(final boolean readable) {
        announcer.ended(StepKind.REQUEST, SYNTHESIS, readable ? StepResult.DONE : StepResult.FAILED);
    }

    private static String label(final int number) {
        return "sample-" + number;
    }
}
