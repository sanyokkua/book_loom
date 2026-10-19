package ua.bookloom.pipeline;

import java.time.Duration;
import java.util.Objects;
import ua.bookloom.pipeline.FaultyModel.Fault;

/**
 * How often each fault strikes, per thousand calls, and when the one long outage begins. The thresholds are cumulative
 * bands of one roll, so the faults of one kind of call never overlap.
 *
 * @param seed the seed every roll is made with
 * @param outageAtCall the call the long outage begins at, counted from one; zero for none
 * @param outage how long the outage lasts on the run's clock
 * @param scale how many times the default rates; zero injects nothing but the outage
 */
record FaultPlan(long seed, int outageAtCall, Duration outage, int scale) {

    /** Rejects a missing outage or a negative scale. */
    FaultPlan {
        Objects.requireNonNull(outage, "outage");
        if (scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
    }

    /** A plan whose outage of 25 minutes begins at {@code outageAtCall}, at the default rates. */
    static FaultPlan standard(final long seed, final int outageAtCall) {
        return new FaultPlan(seed, outageAtCall, Duration.ofMinutes(25), 1);
    }

    int rejudgeHangPerMille() {
        return 300 * scale;
    }

    /** A fault any call may meet. */
    Fault anyCall(final int roll) {
        return band(
                roll,
                new Fault[] {
                    Fault.TIMEOUT,
                    Fault.HANG,
                    Fault.UPSTREAM_BURST,
                    Fault.UNREACHABLE,
                    Fault.MODEL_UNLOADED,
                    Fault.THROW
                },
                new int[] {5, 2, 4, 3, 2, 2});
    }

    /** A fault of a draft's reply; the roll is the same one {@link #anyCall(int)} passed on. */
    Fault draftCall(final int roll) {
        return band(
                roll - anyCallTotal(),
                new Fault[] {
                    Fault.DROP_TOKEN,
                    Fault.DUPLICATE_TOKEN,
                    Fault.GARBLE_TOKEN,
                    Fault.STRAY_BRACKET,
                    Fault.REFUSAL,
                    Fault.EMPTY_TARGET,
                    Fault.EMPTY_COMPLETION
                },
                new int[] {8, 6, 6, 5, 5, 4, 3});
    }

    /** A fault of a judge's reply. */
    Fault judgeCall(final int roll) {
        return band(roll - anyCallTotal(), new Fault[] {Fault.JUDGE_JUNK, Fault.JUDGE_REVISE}, new int[] {20, 40});
    }

    private int anyCallTotal() {
        return 18 * scale;
    }

    private Fault band(final int roll, final Fault[] faults, final int[] perMille) {
        int upTo = 0;
        for (int index = 0; index < faults.length; index++) {
            upTo += perMille[index] * scale;
            if (roll >= 0 && roll < upTo) {
                return faults[index];
            }
        }
        return Fault.NONE;
    }
}
