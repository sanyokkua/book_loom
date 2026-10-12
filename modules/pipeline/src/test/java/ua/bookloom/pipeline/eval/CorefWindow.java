package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;

/**
 * One window of the coreference probe: invented paragraphs that name one target, and every third-person pronoun in
 * them with the gold referent, so a model's answer is scored without a second model.
 *
 * @param id the window id
 * @param target the name the model is asked about
 * @param shape the situation the window tests: {@code next-sentence-other}, {@code speaker-in-quotes},
 *     {@code place-people}, {@code first-person-narrator}, {@code no-referent} or {@code plain}
 * @param paragraphs two to five paragraphs; the window text joins them with a blank line
 * @param pronouns every third-person pronoun of the text
 */
record CorefWindow(String id, String target, String shape, List<String> paragraphs, List<Pronoun> pronouns) {

    /** What a pronoun stands for. */
    enum Referent {
        /** The window's target name. */
        TARGET,
        /** Another character, or a person the text does not name. */
        OTHER,
        /** A place that stands for its people ("her fishermen"): it has no gender to vote. */
        PLACE_PEOPLE,
        /** Nothing the window names. */
        NONE
    }

    /**
     * One pronoun of the text.
     *
     * @param quote a verbatim snippet that holds the pronoun and occurs once in the window text
     * @param pronoun the pronoun word
     * @param referent what it stands for
     * @param who a plain description of the referent
     * @param inSpeech whether the snippet lies inside quoted speech
     */
    record Pronoun(String quote, String pronoun, Referent referent, String who, boolean inSpeech) {}

    /** Rejects missing parts and copies the lists. */
    CorefWindow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(shape, "shape");
        paragraphs = List.copyOf(paragraphs);
        pronouns = List.copyOf(pronouns);
    }

    /** The text the model reads. */
    String text() {
        return String.join("\n\n", paragraphs);
    }
}
