package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;

/** What a provider test would be run against and whether the form stops one; written by the form, FX thread only. */
final class ProviderTestTarget {

    private final ObjectProperty<Optional<TestSubject>> subject = new SimpleObjectProperty<>(Optional.empty());
    private final ReadOnlyBooleanWrapper blocked = new ReadOnlyBooleanWrapper(false);

    ObjectProperty<Optional<TestSubject>> subject() {
        return subject;
    }

    ReadOnlyBooleanProperty blocked() {
        return blocked.getReadOnlyProperty();
    }

    void publish(final Optional<TestSubject> next, final boolean isBlocked) {
        subject.set(Objects.requireNonNull(next, "next"));
        blocked.set(isBlocked);
    }
}
