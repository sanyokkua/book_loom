package ua.bookloom.pipeline.setup;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/**
 * Finds the author part of a suggested file name ("Author. Title. Year") and whether it was left in Latin letters for a
 * target language written in another script, which a model does when it copies the name of a Latin-script author.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FileNameAuthor {

    private static final String PART_SEPARATOR = ". ";

    /**
     * The name's first part: what stands before the first full stop and space, or the whole name when it has none.
     *
     * @param name the cleaned file name
     * @return the author part, possibly the whole name
     */
    static String partOf(final String name) {
        final int end = name.indexOf(PART_SEPARATOR);
        return end > 0 ? name.substring(0, end) : name;
    }

    /**
     * Whether the author part has letters, none of them in the target language's script, for a target that is not
     * written in Latin letters.
     *
     * @param name the cleaned file name
     * @param targetTag the target language's BCP 47 tag, possibly null or unknown
     * @return {@code true} if the author part is still in another script than the target's, {@code false} otherwise,
     *     including when the target is written in Latin letters or its script is not known
     */
    static boolean isLeftInAnotherScript(final String name, @Nullable final String targetTag) {
        final Optional<Script> target = Languages.scriptOf(targetTag)
                .filter(script ->
                        script != Script.LATIN && !script.letterScripts().isEmpty());
        if (target.isEmpty()) {
            return false;
        }
        final String part = partOf(name);
        final boolean hasLetters = part.codePoints().anyMatch(Character::isLetter);
        final boolean inTarget = part.codePoints()
                .filter(Character::isLetter)
                .anyMatch(point -> target.get().letterScripts().contains(Character.UnicodeScript.of(point)));
        return hasLetters && !inTarget;
    }

    /**
     * The instruction for the second ask, naming the part that stayed in Latin.
     *
     * @param name the first answer
     * @return one sentence for the prompt
     */
    static String correction(final String name) {
        return "Correction: your previous answer kept \"" + partOf(name)
                + "\" in Latin letters. Write the author's name"
                + " in the target language's own alphabet, the way a published edition prints it.";
    }
}
