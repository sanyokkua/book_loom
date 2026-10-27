package ua.bookloom.api.pipeline;

/**
 * Why a row of an imported CSV file could not be read, so the import can name each cause once instead of leaving a
 * person with a list of line numbers.
 */
public enum CsvProblem {
    /** A quoted field opens and never closes. */
    OPEN_QUOTE,
    /** The row does not hold one value for each column. */
    FIELD_COUNT,
    /** The row's term is empty. */
    NO_TERM,
    /** The type is not one of the {@link ua.bookloom.api.project.TermType} values. */
    UNKNOWN_TYPE,
    /** The gender is not one of the {@link ua.bookloom.api.project.Gender} values. */
    UNKNOWN_GENDER,
    /** The locked value is neither {@code true} nor {@code false}. */
    UNKNOWN_LOCKED
}
