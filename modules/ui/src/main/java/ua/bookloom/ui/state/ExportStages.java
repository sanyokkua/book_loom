package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ExportProgress;

/**
 * The row index of each export stage among its progress dialog's group rows, so the dialog can tell the stages before
 * the reread, the reread and the writing apart without depending on the order the stages are declared in.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExportStages {

    /** The row of the reread next to the neighbours. */
    public static final int REREAD = 3;

    /** The row of the writing of the file. */
    public static final int WRITING = 4;

    /**
     * The group row a stage is shown in.
     *
     * @param step the stage
     * @return its row, from zero, in the order an export passes through the stages
     */
    public static int rowOf(final ExportProgress.Step step) {
        return switch (step) {
            case VALIDATING -> 0;
            case CONSISTENCY_RETRY -> 1;
            case RETRY_DOUBTED -> 2;
            case CONSISTENCY_NEIGHBOUR -> REREAD;
            case WRITING -> WRITING;
        };
    }
}
