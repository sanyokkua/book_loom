package ua.bookloom.api.llm;

/** The size band a quant tier is judged in. */
public enum SizeClass {
    /** Dense models up to about 4B parameters. */
    SMALL,
    /** Dense models of about 7B to 14B parameters. */
    MID,
    /** Dense models of about 20B to 35B parameters. */
    LARGE,
    /** Mixture-of-experts models with few active parameters. */
    MOE
}
