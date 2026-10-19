package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.DestinationChooser;

/** A hand-written {@link DestinationChooser} that records what it was asked and answers with a scripted file. */
public final class RecordingDestinationChooser implements DestinationChooser {

    private final List<Path> initials = new CopyOnWriteArrayList<>();
    private volatile @Nullable Path answer;

    /** Makes every later choice answer with {@code chosen}; {@code null} answers that the person cancelled. */
    public void answer(final @Nullable Path chosen) {
        answer = chosen;
    }

    @Override
    public Optional<Path> choose(final Path initial) {
        initials.add(Objects.requireNonNull(initial, "initial"));
        return Optional.ofNullable(answer);
    }

    /** The starting file of each choice asked for, in order. */
    public List<Path> initials() {
        return List.copyOf(initials);
    }
}
