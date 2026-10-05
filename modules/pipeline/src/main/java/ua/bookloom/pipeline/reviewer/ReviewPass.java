package ua.bookloom.pipeline.reviewer;

/** Which pass of a batch the reviewer makes: Balanced makes one, Max adds a second with a narrower checklist. */
public enum ReviewPass {

    /** The first pass, with the whole checklist. */
    FIRST(""),

    /** The second pass of Max, which reads only the defects a first read most often misses. */
    SECOND("This is a second pass over text already reviewed once. Check only for gender, terminology and agreement"
            + " errors, and answer ok for everything else.");

    private final String instruction;

    ReviewPass(final String instruction) {
        this.instruction = instruction;
    }

    /**
     * The extra line the prompt carries for this pass.
     *
     * @return the instruction, empty for the first pass
     */
    public String instruction() {
        return instruction;
    }
}
