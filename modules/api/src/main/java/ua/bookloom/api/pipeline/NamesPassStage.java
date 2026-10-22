package ua.bookloom.api.pipeline;

/**
 * The stages of the autonomous names pass, in the order it runs them. The pass announces each as a
 * {@link StepKind#STAGE} step labelled with its {@link #code()}, and the progress dialog names its eight rows from the
 * same list, so a stage added here shows up in both.
 */
public enum NamesPassStage {
    /** Finds the candidate names in the book's text; sends no request. */
    SCAN("scan"),
    /** Decides which candidates are names, from the model's reading of windows of the book. */
    NAMES("names"),
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
