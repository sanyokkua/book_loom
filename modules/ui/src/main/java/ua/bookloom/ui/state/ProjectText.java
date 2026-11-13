package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * How the project actions word what they say.
 *
 * @param messages the catalogue the refusals and language names come from
 * @param languages names languages in the interface language
 * @param errors where an unexpected failure is shown
 */
public record ProjectText(Messages messages, LanguageNames languages, ErrorPresenter errors) {

    /** Rejects a missing part. */
    @Inject
    public ProjectText {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(languages, "languages");
        Objects.requireNonNull(errors, "errors");
    }
}
