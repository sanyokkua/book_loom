package ua.bookloom.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.Segment;

/** The decided segment and its reason when the decision is flagged. */
record Decision(Segment segment, @Nullable AppError flagReason) {

    Decision {
        Objects.requireNonNull(segment, "segment");
    }
}
