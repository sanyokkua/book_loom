package ua.bookloom.pipeline.heal;

import java.util.List;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * How a segment drafted in pieces is repaired: each piece is drafted again with the findings as its instruction,
 * because the whole segment is too large for any single repair call and a piece that has the fault is the only place
 * a finding can be named.
 */
@FunctionalInterface
public interface PieceRedraft {

    /**
     * Drafts every piece again and joins the replies in order.
     *
     * @param findings the findings to name to every piece; empty for a plain redraft
     * @param calls the seam every piece's call goes through
     * @return the joined masked target as a {@link RepairReply}, or the error of a call the run must route
     */
    Result<RepairReply> redraft(List<QaFinding> findings, ModelCalls calls);
}
