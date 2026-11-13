package ua.bookloom.api.pipeline;

/**
 * The stages of the autonomous names pass, in the order it runs them. The pass announces each as a
 * {@link StepKind#STAGE} step labelled with its {@link #code()}, and the progress dialog names its rows from the
 * same list, so a stage added here shows up in both.
 */
public enum NamesPassStage {
    /** Finds the candidate names in the book's text; sends no request. */
    SCAN("scan"),
    /** Decides which candidates are names, from the model's reading of windows of the book. */
    NAMES("names"),
    /** Asks the model the most probable gender and kind of each name nobody has settled, by the bare name. */
    NAMES_KNOWLEDGE("names-knowledge"),
    /** Finds the repeated words in the text; sends no request. */
    TERMS_SCAN("terms-scan"),
    /** Cleans the repeated words the lexicon already holds. */
    TERMS_REVIEW("terms-review"),
    /** Reads the repeated words in context. */
    TERMS_READ("terms-read"),
    /** Suggests a translation for each name. */
    NAMES_TRANSLATE("names-translate"),
    /** Suggests a translation for each repeated word. */
    TERMS_TRANSLATE("terms-translate"),
    /**
     * Tests every staged suggestion against the target language's rules; sends no request and accepts nothing, since
     * accepting is the person's Accept all.
     */
    CHECK("check"),
    /** Writes what the pass changed to both tables, in one go; sends no request. */
    WRITE("write");

    private final String code;

    NamesPassStage(final String code) {
        this.code = code;
    }

    /** {@return the label the pass announces the stage's step with} */
    public String code() {
        return code;
    }
}
