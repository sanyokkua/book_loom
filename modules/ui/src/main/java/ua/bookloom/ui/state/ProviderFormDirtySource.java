package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ViewNames;

/** Lets the leave guard ask about the Providers form's unsaved changes before another screen opens. */
public final class ProviderFormDirtySource implements DirtySource {

    private final ProviderForm form;

    /**
     * Wraps the form.
     *
     * @param form the form whose changes are guarded
     */
    @Inject
    public ProviderFormDirtySource(final ProviderForm form) {
        this.form = Objects.requireNonNull(form, "form");
    }

    @Override
    public ViewNames home() {
        return ViewNames.SETTINGS;
    }

    @Override
    public boolean isDirty() {
        return form.dirty().get();
    }

    @Override
    public @Nullable String subject() {
        final String name = form.subject();
        return name.isEmpty() ? null : name;
    }

    @Override
    public void discard() {
        form.discard();
    }

    @Override
    public void save(final Runnable then) {
        form.save(then);
    }
}
