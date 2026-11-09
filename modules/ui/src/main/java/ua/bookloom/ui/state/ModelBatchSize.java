package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSelection;

/**
 * The batch size of the model the run would use, which turns the dial's "one call per batch of paragraphs" into a
 * number on Generation and the Book Brief. Binding a model makes no call; a choice that cannot be bound, or none at
 * all, is planned with the built-in size.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ModelBatchSize {

    private final ChatModelFactory models;
    private final SettingsViewModel settings;

    /**
     * Reads the chosen model's profile.
     *
     * @return the most segments one draft call carries; positive; FX thread only
     */
    public int current() {
        final @Nullable ModelSelection chosen = settings.selection().orElse(null);
        final @Nullable ChatModel model = chosen == null
                ? null
                : Objects.requireNonNull(models, "models").create(chosen).data();
        final int size = model == null
                ? ModelProfile.DEFAULT_BATCH_SIZE
                : model.profile().batchSize();
        log.debug("batch size {} (model chosen {}, bound {})", size, chosen != null, model != null);
        return size;
    }
}
