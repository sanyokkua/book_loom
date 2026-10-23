package ua.bookloom.ui.state;

import java.util.LinkedHashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The order one provider's models are offered in: those it listed, then the person's own, then those with settings. */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ModelOrder {

    static List<String> merge(final List<String> listed, final List<String> custom, final List<String> stored) {
        final LinkedHashSet<String> once = new LinkedHashSet<>(listed);
        once.addAll(custom);
        stored.stream().sorted().forEach(once::add);
        return List.copyOf(once);
    }
}
