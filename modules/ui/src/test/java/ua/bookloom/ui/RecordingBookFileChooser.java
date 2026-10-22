package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.BookFileChooser;

/** A hand-written {@link BookFileChooser} that records each ask and answers with a scripted file. */
public final class RecordingBookFileChooser implements BookFileChooser {

    private final List<@Nullable Window> owners = new CopyOnWriteArrayList<>();
    private volatile @Nullable Path answer;

    /** Makes every later choice answer with {@code chosen}; {@code null} answers that the person cancelled. */
    public void answer(final @Nullable Path chosen) {
        answer = chosen;
    }

    /** The owner window of each choice asked, in order. */
    public List<@Nullable Window> owners() {
        return List.copyOf(owners);
    }

    @Override
    public Optional<Path> chooseToOpen(final @Nullable Window owner) {
        owners.add(owner);
        return Optional.ofNullable(answer);
    }
}
