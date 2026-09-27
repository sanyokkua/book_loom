package ua.bookloom.api.pipeline;

/**
 * A side file an export can write beside the translated book.
 */
public enum SideFile {

    /** The project's glossary, as {@code <name>.glossary.csv}. */
    GLOSSARY_CSV,

    /** A source/target bilingual review table, as {@code <name>.bilingual.html}. */
    BILINGUAL_HTML,

    /** A counts-and-findings report, as {@code <name>.report.md}. */
    QUALITY_REPORT
}
