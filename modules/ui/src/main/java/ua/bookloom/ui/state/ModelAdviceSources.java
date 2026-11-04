package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.i18n.LanguageNames;

/**
 * What the advice lines of the Models tab are built from, injected as one.
 *
 * @param reader where the readings come from
 * @param current the open project, whose brief holds the language pair
 * @param names how a language tag is named in the interface language
 */
public record ModelAdviceSources(AdviceReader reader, CurrentProject current, LanguageNames names) {

    /** Rejects a missing part. */
    @Inject
    public ModelAdviceSources {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(names, "names");
    }
}
