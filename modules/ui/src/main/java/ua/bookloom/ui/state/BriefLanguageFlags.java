package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.api.project.BookBrief;

/** What the brief's two languages say about themselves, worked out again whenever the brief changes. */
@Slf4j
final class BriefLanguageFlags {

    private final LanguageSupport languages;
    private final ReadOnlyBooleanWrapper sourceUndeclared = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper sameLanguage = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper targetUntested = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canContinue = new ReadOnlyBooleanWrapper(false);

    BriefLanguageFlags(final LanguageSupport languages) {
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    ReadOnlyBooleanProperty sourceUndeclared() {
        return sourceUndeclared.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty sameLanguage() {
        return sameLanguage.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty targetUntested() {
        return targetUntested.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty canContinue() {
        return canContinue.getReadOnlyProperty();
    }

    void derive(final @Nullable BookBrief brief) {
        final String source = brief == null ? null : brief.sourceLanguage();
        final String target = brief == null ? null : brief.targetLanguage();
        final boolean same = source != null && source.equals(target);
        sourceUndeclared.set(brief != null && source == null);
        sameLanguage.set(same);
        targetUntested.set(target != null && !languages.hasTestedRules(target));
        canContinue.set(BriefLanguages.isUsable(source, target));
        log.debug("languages {} -> {}: same {}, untested {}", source, target, same, targetUntested.get());
    }
}
