package ua.bookloom.pipeline.heal;

import java.util.List;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * How the one repair of a segment drafted whole is sent. The draft step builds it with the context and the mask the
 * draft was shown, so the quality loop can ask for the repair without knowing how a draft's prompt is made.
 */
@FunctionalInterface
public interface RepairCall {

    /**
     * Sends the repair and reads its reply.
     *
     * @param previous the non-null masked text the repair rewrites, shown to the model as data
     * @param defects the non-null findings to fix, each with its explanation and the span it quotes
     * @param calls the non-null seam the call goes through
     * @return the reply as a {@link RepairReply}, or the error of a call the run must route
     */
    Result<RepairReply> send(String previous, List<QaFinding> defects, ModelCalls calls);
}
