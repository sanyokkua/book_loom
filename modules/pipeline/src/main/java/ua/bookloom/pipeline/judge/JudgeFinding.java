package ua.bookloom.pipeline.judge;

import java.util.Objects;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/**
 * One judge finding against a segment, its label already translated back to that segment's real id.
 *
 * @param segmentId the segment the finding is against
 * @param type the defect kind as the judge wrote it (the catalogue's {@code tag} is the checks' {@code markup})
 * @param severity how serious the finding is
 * @param note the judge's short explanation
 */
public record JudgeFinding(String segmentId, String type, Severity severity, String note) {

    /** Rejects an incomplete finding at the boundary. */
    public JudgeFinding {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(note, "note");
    }

    /**
     * Converts this finding to the shared quality-gate finding shape.
     *
     * @return the equivalent {@link QaFinding}, raised by {@code "judge"}
     */
    public QaFinding toQaFinding() {
        return new QaFinding(type, severity, note, "judge");
    }
}
