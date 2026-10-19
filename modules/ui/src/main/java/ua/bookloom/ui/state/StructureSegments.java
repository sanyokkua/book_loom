package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.ErrorCode;

/** Where the structure screen's segment list stands for the part of the book the person picked. */
public sealed interface StructureSegments {

    /** No part is picked, or the book has none to list. */
    record Idle() implements StructureSegments {}

    /** The listing was asked for and has not answered. */
    record Loading() implements StructureSegments {}

    /**
     * The listing could not be made.
     *
     * @param code why, so the screen words it without showing a raw message
     */
    record Failed(ErrorCode code) implements StructureSegments {

        /** Rejects a missing code. */
        public Failed {
            Objects.requireNonNull(code, "code");
        }
    }

    /**
     * The listing answered.
     *
     * @param rows the segments in reading order, unmodifiable
     * @param chunks how many planned chunks they fall into, summed over the parts listed
     */
    record Loaded(List<StructureSegmentRow> rows, int chunks) implements StructureSegments {

        /** Copies the rows and rejects a negative count. */
        public Loaded {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
            if (chunks < 0) {
                throw new IllegalArgumentException("chunks must be >= 0");
            }
        }
    }
}
