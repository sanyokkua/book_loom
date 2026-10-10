package ua.bookloom.pipeline.revision;

import java.util.Map;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.util.hash.HashUtil;

/**
 * The hash of everything one check against the neighbours shows the model, so that the same hash means the same
 * question: the slots of {@link NeighbourPrompt#slots} (the paragraph's source and target, both neighbours, the glossary
 * and recurring-term lines, the characters, the summary), how the guards compare, the languages and the style sheet.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ParagraphFingerprint {

    private static final char FIELD_SEPARATOR = '\1';
    private static final char VALUE_SEPARATOR = '\2';

    /**
     * Fingerprints one check.
     *
     * @param inputs what the pass read as it started
     * @param user the slots the check's prompt is filled from
     * @param mode how the guards compare the counts
     * @return the lowercase hex SHA-256 of the question
     */
    static String of(final PassInputs inputs, final Map<String, String> user, final RevisionGuards.Mode mode) {
        return of(inputs, user, mode.toString());
    }

    /**
     * Fingerprints one question of a pass step.
     *
     * @param inputs what the pass read as it started
     * @param user everything the step's question is built from, by slot
     * @param step which step asks, so two steps never share a fingerprint
     * @return the lowercase hex SHA-256 of the question
     */
    static String of(final PassInputs inputs, final Map<String, String> user, final String step) {
        final StringBuilder question = new StringBuilder()
                .append(step)
                .append(FIELD_SEPARATOR)
                .append(inputs.frame().sourceLanguage())
                .append(FIELD_SEPARATOR)
                .append(inputs.frame().targetLanguage())
                .append(FIELD_SEPARATOR)
                .append(inputs.frame().styleSheet().hash());
        new TreeMap<>(user)
                .forEach((slot, value) -> question.append(FIELD_SEPARATOR)
                        .append(slot)
                        .append(VALUE_SEPARATOR)
                        .append(value));
        final String hash = HashUtil.sha256OfNfcText(question.toString());
        log.trace("Paragraph fingerprint {} for {}", hash, user.get("text"));
        return hash;
    }
}
