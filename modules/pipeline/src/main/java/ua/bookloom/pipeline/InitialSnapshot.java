package ua.bookloom.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.Document;

/** The source model and an optional error reported while releasing its opened representation. */
record InitialSnapshot(Document document, @Nullable AppError releaseError) {

    InitialSnapshot {
        Objects.requireNonNull(document, "document");
    }
}
