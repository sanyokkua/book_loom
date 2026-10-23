package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ViewNames;

/** Lets the leave guard ask about the unsaved changes of the Providers form and the Models form before another screen opens. */
public final class SettingsDirtySource implements DirtySource {

    private final ProviderForm form;
    private final ModelForm models;

    /**
     * Wraps the forms.
     *
     * @param form the Providers form
     * @param models the Models form
     */
    @Inject
    public SettingsDirtySource(final ProviderForm form, final ModelForm models) {
        this.form = Objects.requireNonNull(form, "form");
        this.models = Objects.requireNonNull(models, "models");
    }

    @Override
    public ViewNames home() {
        return ViewNames.SETTINGS;
    }

    @Override
    public boolean isDirty() {
        return form.dirty().get() || models.dirty().get();
    }

    @Override
    public @Nullable UnsavedSubject subject() {
        if (!form.dirty().get() && models.dirty().get()) {
            return UnsavedSubject.model(models.subject());
        }
        final String name = form.subject();
        return name.isEmpty() ? null : UnsavedSubject.provider(name);
    }

    @Override
    public void discard() {
        if (form.dirty().get()) {
            form.discard();
        }
        if (models.dirty().get()) {
            models.discard();
        }
    }

    // The Providers form first, then the Models form; a failure in either leaves the person where they are.
    @Override
    public void save(final Runnable then) {
        Objects.requireNonNull(then, "then");
        final Runnable afterProviders = models.dirty().get() ? () -> models.save(then) : then;
        if (form.dirty().get()) {
            form.save(afterProviders);
        } else {
            afterProviders.run();
        }
    }
}
