package ua.bookloom.pipeline.glossary;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/** The scripts a suggested target is read against: the one it must not stay in, and the one its letters must be in. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionScripts {

    /**
     * The script a suggested name must not stay in: under Transliterate, the source's, when the target is written in
     * another one — a copied name is no suggestion there. Under Translate a kept foreign name can be right.
     */
    static @Nullable Script refused(final CallFrame frame, final NamePolicy policy) {
        if (policy != NamePolicy.TRANSLITERATE) {
            return null;
        }
        final Optional<Script> source = Languages.scriptOf(frame.analysisLanguage());
        final Optional<Script> target = Languages.scriptOf(frame.targetLanguage());
        final boolean differs = source.isPresent() && target.isPresent() && source.get() != target.get();
        log.debug("Suggestion script rule source={} target={} refusesSourceScript={}", source, target, differs);
        return differs ? source.get() : null;
    }

    /** The target language's script when it has letters of its own: every letter of a suggestion must be one. */
    static @Nullable Script required(final CallFrame frame) {
        final Optional<Script> target = Languages.scriptOf(frame.targetLanguage())
                .filter(script -> !script.letterScripts().isEmpty());
        log.debug("Suggestion required script {}", target);
        return target.orElse(null);
    }
}
