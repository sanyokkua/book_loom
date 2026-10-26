package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.FileChooserPort;

/** A hand-written {@link FileChooserPort} that records what it was asked and answers with a scripted file. */
public final class RecordingFileChooser implements FileChooserPort {

    private final List<String> asked = new CopyOnWriteArrayList<>();
    private final List<@Nullable Window> owners = new CopyOnWriteArrayList<>();
    private final List<Path> initials = new CopyOnWriteArrayList<>();
    private volatile @Nullable Path answer;

    /** Makes every later choice answer with {@code chosen}; {@code null} answers that the person cancelled. */
    public void answer(final @Nullable Path chosen) {
        answer = chosen;
    }

    /** Each choice asked for, in order: {@code open}, or {@code save:<starting file>}. */
    public List<String> asked() {
        return List.copyOf(asked);
    }

    /** The owner window each choice was asked with, in order; {@code null} for a dialog without one. */
    public List<@Nullable Window> owners() {
        return List.copyOf(owners);
    }

    /** The starting file of each save asked for, in order. */
    public List<Path> initials() {
        return List.copyOf(initials);
    }

    @Override
    public Optional<Path> chooseToOpen(final @Nullable Window owner, final FileKind kind) {
        owners.add(owner);
        asked.add("open");
        return Optional.ofNullable(answer);
    }

    @Override
    public Optional<Path> chooseToSave(final @Nullable Window owner, final FileKind kind, final Path initial) {
        owners.add(owner);
        initials.add(Objects.requireNonNull(initial, "initial"));
        asked.add("save:" + initial);
        return Optional.ofNullable(answer);
    }
}
