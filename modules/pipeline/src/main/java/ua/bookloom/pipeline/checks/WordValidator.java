package ua.bookloom.pipeline.checks;

import java.util.List;

/**
 * The port that decides whether a translated word is a real word of the target language. A garbled or coined word
 * ({@code кафедрахрі}) is written in the right script, so the script checks cannot see it; a validator can, by a
 * dictionary or by a model. What it finds is only ever a soft note: a rare or dialect word a dictionary lacks must not
 * stop a book, so the port is bound to {@link #none()} because no dictionary of words is bundled or planned.
 */
@FunctionalInterface
public interface WordValidator {

    /**
     * Finds the words of a target text that are not real words.
     *
     * @param target the target's display text, protected tokens and kept names already out
     * @param targetLanguage the target language tag
     * @return one soft {@link FindingKind#UNKNOWN_WORD} finding per doubtful word, in text order, each spanning that
     *     word; never null, empty when every word is real or the validator has no opinion
     */
    List<CheckFinding> find(String target, String targetLanguage);

    /**
     * The validator that has no opinion, so a run behaves as if the port did not exist.
     *
     * @return a validator whose {@link #find} is always empty
     */
    static WordValidator none() {
        return NoWordValidator.INSTANCE;
    }
}
