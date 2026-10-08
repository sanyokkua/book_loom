package ua.bookloom.pipeline.eval;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.heal.DraftJudge;
import ua.bookloom.pipeline.heal.DraftOutcome;

/**
 * Judges a reply for the eval's first segment by the run's own classes: the draft step's adoption of a masked target
 * through the placeholder gate, then {@link DraftJudge} (hard gates, text checks, residue, typography, quote repair and
 * the acceptance rule). No replica of any of these lives in the eval.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReplyJudge {

    /**
     * What the run would make of the reply.
     *
     * @param gatePassed whether the placeholder gate restored it (tokens in order, protected spans back)
     * @param accepted whether the acceptance rule takes it as drafted
     */
    record Verdict(boolean gatePassed, boolean accepted) {}

    private static final Verdict REFUSED = new Verdict(false, false);

    static Verdict judge(final EvalProject project, final int index, final String maskedReply) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(maskedReply, "maskedReply");
        final Optional<DraftOutcome> adopted =
                project.translator().adopt(project.judged(index), project.mask(index), maskedReply);
        if (adopted.isEmpty() || !(adopted.get() instanceof DraftOutcome.Drafted drafted)) {
            return REFUSED;
        }
        final DraftJudge.Judged judged = DraftJudge.judge(drafted, project.loop(), project.gate());
        return new Verdict(true, judged.accepted());
    }
}
