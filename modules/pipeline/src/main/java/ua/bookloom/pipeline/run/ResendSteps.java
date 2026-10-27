package ua.bookloom.pipeline.run;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/**
 * The resend steps of one model call: the provider client's retry after a timeout is work the progress bar counts, so
 * each attempt past the first is one step, ended when that attempt ends.
 */
@Slf4j
final class ResendSteps {

    private final StepAnnouncer steps;
    private final int callNumber;
    private final @Nullable String segmentId;
    private @Nullable String openKey;

    ResendSteps(final StepAnnouncer steps, final int callNumber, @Nullable final String segmentId) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.callNumber = callNumber;
        this.segmentId = segmentId;
    }

    void started(final CallAttempt attempt) {
        if (attempt.number() <= 1) {
            return;
        }
        final String key = "resend:" + callNumber + ":" + attempt.number();
        log.debug("Resend step attempt={} key={}", attempt.number(), key);
        openKey = key;
        steps.added(StepKind.RESEND, "resend-" + attempt.number(), key, segmentId);
    }

    void ended(final StepResult result) {
        final String key = openKey;
        if (key != null) {
            openKey = null;
            steps.ended(StepKind.RESEND, key, result);
        }
    }
}
