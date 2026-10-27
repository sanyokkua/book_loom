package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.Paused;

/** How many failures of one kind a segment's budget absorbs when a person resumes or an outage is waited out. */
class TranslationJobOutageRecoveryTest extends UnattendedRecoveryHarness {

    // A request the provider rejects and the person sends again twice is spent on the segment: the third failure flags
    // it.
    @Test
    void run_validationErrorResumedByThePersonTwice_isFlaggedOnTheThirdAndTheRunGoesOn() {
        final ScriptedChatModel model =
                replies("ONE.").answerTimes(3, error(ErrorCode.validation)).answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});
        job.subscribe(event -> resumeOnPause(job, event));

        final JobReport report = report(job.run());

        assertThat(report).extracting(JobReport::end, JobReport::accepted).containsExactly(JobState.COMPLETED, 2);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:1", ErrorCode.validation));
        assertThat(pauses()).extracting(Paused::pauses).containsExactly(1, 2);
        assertThat(waits()).isEmpty();
    }

    // An outage costs the segment nothing: ten failures are waited out and the eleventh call is accepted.
    @Test
    void run_outageTenTimes_recoversTenTimesAndAcceptsTheSegmentOnTheEleventhCall() {
        final ScriptedChatModel model = replies("ONE.")
                .answerTimes(10, error(ErrorCode.unreachable))
                .answer(target("TWO."))
                .answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});

        final JobReport report = report(job.run());

        assertThat(report)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(pauses()).extracting(Paused::pauses).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    // The eleventh failure in a row exhausts the outage budget: the segment is flagged unreachable and the run goes on.
    @Test
    void run_outageEleventhFailure_flagsTheSegmentWithUnreachableAndGoesOn() {
        final ScriptedChatModel model =
                replies("ONE.").answerTimes(11, error(ErrorCode.unreachable)).answer(target("THREE."));
        final TranslationJobImpl job = job(model, wake -> {});

        final JobReport report = report(job.run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:1", ErrorCode.unreachable));
        assertThat(pauses()).extracting(Paused::pauses).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    private static void resumeOnPause(final TranslationJobImpl job, final JobEvent event) {
        if (event instanceof Paused) {
            job.resume();
        }
    }
}
