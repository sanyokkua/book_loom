package ua.bookloom.ui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.ReviewModeChoice;

/** Remembers each mode Settings told it about, and names a launch override when a test sets one. */
public final class RecordingReviewModeChoice implements ReviewModeChoice {

    private final List<ReviewMode> told = new CopyOnWriteArrayList<>();
    private volatile @Nullable String override;

    /** Makes the launch variable or property {@code name} decide the review mode. */
    public RecordingReviewModeChoice overriddenBy(final String name) {
        override = name;
        return this;
    }

    /** The modes told so far, oldest first. */
    public List<ReviewMode> told() {
        return List.copyOf(told);
    }

    @Override
    public Optional<String> launchOverride() {
        return Optional.ofNullable(override);
    }

    @Override
    public void stored(final ReviewMode mode) {
        told.add(mode);
    }
}
