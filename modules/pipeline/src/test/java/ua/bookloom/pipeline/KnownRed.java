package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The known-failure mechanism of the real-run corpus (a case flagged {@code knownFailure} with a {@code fixedBy} task,
 * reported KNOWN_RED, and a test that fails the day the code gets it right) for a case that needs a whole job, a
 * consistency pass or a call of its own and so cannot be a row of a corpus file. The assertions inside are the
 * behaviour the fixing task must deliver; while that task is open they must still fail, and once it lands the call
 * fails with a message that says to drop the wrapper and keep the assertions as a plain test.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KnownRed {

    private static final String TASK = "15i\\.T\\d+";

    /**
     * Runs {@code assertions}, which must fail with an assertion error until the task {@code fixedBy} has landed.
     *
     * @param fixedBy the 15i task that makes the assertions hold, such as {@code 15i.T2}
     * @param caseId the case, named in the message that asks for the wrapper to be dropped
     * @param assertions the behaviour the task delivers; an assertion error is the expected, red outcome
     */
    public static void until(final String fixedBy, final String caseId, final Runnable assertions) {
        Objects.requireNonNull(fixedBy, "fixedBy");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(assertions, "assertions");
        assertThat(fixedBy).as(caseId + " names the task that fixes it").matches(TASK);
        assertThat(catchThrowable(assertions::run))
                .as(caseId + " now passes: drop KnownRed.until and keep the assertions (task " + fixedBy + ")")
                .isInstanceOf(AssertionError.class);
    }
}
