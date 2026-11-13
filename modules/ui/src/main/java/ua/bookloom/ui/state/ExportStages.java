package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ExportProgress;

/**
 * The row index of each export stage among its progress dialog's group rows, so the dialog can tell the stages before
 * the writing and the writing apart without depending on the order the stages are declared in.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExportStages {

    /** The row of the writing of the file. */
    public static final int WRITING = 1;

    /**
     * The group row a stage is shown in.
     *
     * @param step the stage
     * @return its row, from zero, in the order an export passes through the stages
     */
    public static int rowOf(final ExportProgress.Step step) {
        return switch (step) {
            case VALIDATING -> 0;
            case WRITING -> WRITING;
        };
    }
}
